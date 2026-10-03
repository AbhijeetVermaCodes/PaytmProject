package com.ticketbooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.dto.*;
import com.ticketbooking.exception.BadRequestException;
import com.ticketbooking.exception.DomainConflictException;
import com.ticketbooking.exception.ForbiddenException;
import com.ticketbooking.exception.ResourceNotFoundException;
import com.ticketbooking.model.*;
import com.ticketbooking.repository.EventRequestRepository;
import com.ticketbooking.repository.IdempotencyRecordRepository;
import com.ticketbooking.repository.ReservationRepository;
import com.ticketbooking.repository.SeatRepository;
import com.ticketbooking.repository.ShowRepository;
import com.ticketbooking.security.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.ticketbooking.observability.MetricsService;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final EventRequestRepository eventRequestRepository;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    @Value("${booking.default-per-user-limit:4}")
    private int defaultPerUserLimit;

    @Value("${booking.hold-duration-seconds:120}")
    private long holdDurationSeconds;

    public BookingService(ShowRepository showRepository,
                          SeatRepository seatRepository,
                          ReservationRepository reservationRepository,
                          IdempotencyRecordRepository idempotencyRecordRepository,
                          EventRequestRepository eventRequestRepository,
                          MetricsService metricsService,
                          ObjectMapper objectMapper) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.eventRequestRepository = eventRequestRepository;
        this.metricsService = metricsService;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new show directly.
     */
    @Transactional
    public ShowResponse createShow(CreateShowRequest request) {
        return createShow(request, null);
    }

    /**
     * Creates a new show with assigned available seats and specified owner/manager.
     */
    @Transactional
    public ShowResponse createShow(CreateShowRequest request, String ownerUserId) {
        if (!StringUtils.hasText(request.getName())) {
            throw new BadRequestException("Show name cannot be blank");
        }
        if (request.getSeats() == null || request.getSeats().isEmpty()) {
            throw new BadRequestException("Seats list cannot be empty");
        }
        if (request.getPricePaise() <= 0) {
            throw new BadRequestException("Price in paise must be strictly positive");
        }

        List<String> rawSeats = request.getSeats().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toList());

        Set<String> uniqueSeats = new HashSet<>(rawSeats);
        if (uniqueSeats.size() != rawSeats.size()) {
            throw new BadRequestException("Duplicate seat numbers detected in show creation request");
        }

        int limit = request.getPerUserLimit() != null && request.getPerUserLimit() > 0
                ? request.getPerUserLimit()
                : defaultPerUserLimit;

        Show show = new Show(request.getName().trim(), request.getPricePaise(), limit, ownerUserId);
        show.setTotalSeats(rawSeats.size());
        Show savedShow = showRepository.save(show);

        List<Seat> seatsToSave = new ArrayList<>(rawSeats.size());
        for (String seatNumber : rawSeats) {
            seatsToSave.add(new Seat(savedShow, seatNumber));
        }
        seatRepository.saveAll(seatsToSave);

        log.info("Created show id={} name='{}' total_seats={} per_user_limit={} price_paise={} owner='{}'",
                savedShow.getId(), savedShow.getName(), savedShow.getTotalSeats(), limit, savedShow.getPricePaise(), ownerUserId);

        return getShowState(savedShow.getId());
    }

    /**
     * Submits an event creation request/proposal (starts in PENDING state).
     */
    @Transactional
    public EventProposalResponse submitEventProposal(SubmitEventProposalRequest request, String requestedBy) {
        if (!StringUtils.hasText(requestedBy)) {
            throw new BadRequestException("Authentication required to submit an event proposal");
        }
        if (!StringUtils.hasText(request.getName())) {
            throw new BadRequestException("Event name cannot be blank");
        }
        if (request.getSeats() == null || request.getSeats().isEmpty()) {
            throw new BadRequestException("Seats list cannot be empty");
        }
        if (request.getPricePaise() <= 0) {
            throw new BadRequestException("Price in paise must be strictly positive");
        }

        List<String> rawSeats = request.getSeats().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toList());

        Set<String> uniqueSeats = new HashSet<>(rawSeats);
        if (uniqueSeats.size() != rawSeats.size()) {
            throw new BadRequestException("Duplicate seat numbers detected in event proposal");
        }

        int limit = request.getPerUserLimit() != null && request.getPerUserLimit() > 0
                ? request.getPerUserLimit()
                : defaultPerUserLimit;

        EventRequest eventRequest = new EventRequest(
                request.getName().trim(),
                rawSeats,
                request.getPricePaise(),
                limit,
                requestedBy
        );

        EventRequest saved = eventRequestRepository.save(eventRequest);
        log.info("Event proposal submitted id={} name='{}' requestedBy='{}' seats={}",
                saved.getId(), saved.getName(), requestedBy, rawSeats.size());

        return toEventProposalResponse(saved);
    }

    /**
     * Lists event proposals. Admins can see all, regular users see their own.
     */
    @Transactional(readOnly = true)
    public List<EventProposalResponse> getEventProposals(String currentUser, boolean isAdmin, EventRequestStatus status) {
        List<EventRequest> list;
        if (isAdmin) {
            if (status != null) {
                list = eventRequestRepository.findByStatusOrderByCreatedAtDesc(status);
            } else {
                list = eventRequestRepository.findAllByOrderByCreatedAtDesc();
            }
        } else {
            if (!StringUtils.hasText(currentUser)) {
                return Collections.emptyList();
            }
            list = eventRequestRepository.findByRequestedByOrderByCreatedAtDesc(currentUser);
            if (status != null) {
                list = list.stream().filter(e -> e.getStatus() == status).collect(Collectors.toList());
            }
        }
        return list.stream().map(this::toEventProposalResponse).collect(Collectors.toList());
    }

    /**
     * Gets a single event proposal.
     */
    @Transactional(readOnly = true)
    public EventProposalResponse getEventProposalById(String id, String currentUser, boolean isAdmin) {
        EventRequest eventRequest = eventRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event proposal not found with id: " + id));

        if (!isAdmin && (currentUser == null || !currentUser.equals(eventRequest.getRequestedBy()))) {
            throw new ForbiddenException("Cannot view an event proposal submitted by another user");
        }

        return toEventProposalResponse(eventRequest);
    }

    /**
     * Approves an event proposal and creates the live show with the requester as the owner.
     */
    @Transactional
    public EventProposalResponse approveEventProposal(String proposalId, String adminUserId) {
        if (!UserContext.isAdmin()) {
            throw new ForbiddenException("Only administrators can approve event proposals");
        }

        EventRequest proposal = eventRequestRepository.findById(proposalId)
                .orElseThrow(() -> new ResourceNotFoundException("Event proposal not found with id: " + proposalId));

        if (proposal.getStatus() != EventRequestStatus.PENDING) {
            throw new BadRequestException("Event proposal is already " + proposal.getStatus());
        }

        // Create the show with the requester set as the owner
        CreateShowRequest showRequest = new CreateShowRequest(
                proposal.getName(),
                proposal.getSeats(),
                proposal.getPricePaise(),
                proposal.getPerUserLimit()
        );

        ShowResponse createdShow = createShow(showRequest, proposal.getRequestedBy());

        proposal.setStatus(EventRequestStatus.APPROVED);
        proposal.setReviewedBy(adminUserId);
        proposal.setReviewedAt(Instant.now());
        proposal.setShowId(createdShow.getId());
        EventRequest updated = eventRequestRepository.save(proposal);

        log.info("Event proposal APPROVED id={} by='{}' -> created showId={} with owner='{}'",
                proposalId, adminUserId, createdShow.getId(), proposal.getRequestedBy());

        return toEventProposalResponse(updated);
    }

    /**
     * Rejects an event proposal with an optional explanation.
     */
    @Transactional
    public EventProposalResponse rejectEventProposal(String proposalId, String adminUserId, String reason) {
        if (!UserContext.isAdmin()) {
            throw new ForbiddenException("Only administrators can reject event proposals");
        }

        EventRequest proposal = eventRequestRepository.findById(proposalId)
                .orElseThrow(() -> new ResourceNotFoundException("Event proposal not found with id: " + proposalId));

        if (proposal.getStatus() != EventRequestStatus.PENDING) {
            throw new BadRequestException("Event proposal is already " + proposal.getStatus());
        }

        proposal.setStatus(EventRequestStatus.REJECTED);
        proposal.setReviewedBy(adminUserId);
        proposal.setReviewedAt(Instant.now());
        proposal.setRejectionReason(reason);
        EventRequest updated = eventRequestRepository.save(proposal);

        log.info("Event proposal REJECTED id={} by='{}' reason='{}'", proposalId, adminUserId, reason);

        return toEventProposalResponse(updated);
    }

    private EventProposalResponse toEventProposalResponse(EventRequest e) {
        return new EventProposalResponse(
                e.getId(),
                e.getName(),
                e.getSeats(),
                e.getPricePaise(),
                e.getPerUserLimit(),
                e.getRequestedBy(),
                e.getStatus(),
                e.getReviewedBy(),
                e.getShowId(),
                e.getRejectionReason(),
                e.getCreatedAt(),
                e.getReviewedAt()
        );
    }

    /**
     * Retrieves the state and per-seat status of a show, verifying the reconciliation invariant.
     * Authorized managers (Admins or the Show Owner) can see customer identity details.
     */
    @Transactional(readOnly = true)
    public ShowResponse getShowState(String showId) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Show not found with id: " + showId));

        List<Seat> seats = seatRepository.findByShowIdOrderBySeatNumberAsc(showId);

        long availableCount = 0;
        long heldCount = 0;
        long confirmedCount = 0;
        List<SeatDto> seatDtos = new ArrayList<>(seats.size());

        String currentUser = UserContext.getCurrentUser();
        boolean isAuthorizedManager = UserContext.isAdmin()
                || (show.getOwnerUserId() != null && show.getOwnerUserId().equals(currentUser));

        Map<String, String> reservationUserMap = Collections.emptyMap();
        if (isAuthorizedManager) {
            List<Reservation> reservations = reservationRepository.findByShowId(showId);
            reservationUserMap = reservations.stream()
                    .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED || r.getStatus() == ReservationStatus.HELD)
                    .collect(Collectors.toMap(Reservation::getId, Reservation::getUserId, (u1, u2) -> u1));
        }

        Instant now = Instant.now();
        for (Seat s : seats) {
            SeatStatus effectiveStatus = s.getStatus();
            // Check if hold has expired
            if (effectiveStatus == SeatStatus.HELD && s.getHeldUntil() != null && s.getHeldUntil().isBefore(now)) {
                effectiveStatus = SeatStatus.AVAILABLE;
            }

            switch (effectiveStatus) {
                case AVAILABLE -> availableCount++;
                case HELD -> heldCount++;
                case CONFIRMED -> confirmedCount++;
            }

            String bookedBy = null;
            String resId = null;
            if (isAuthorizedManager && s.getReservationId() != null && effectiveStatus != SeatStatus.AVAILABLE) {
                bookedBy = reservationUserMap.get(s.getReservationId());
                resId = s.getReservationId();
            }

            seatDtos.add(new SeatDto(s.getSeatNumber(), effectiveStatus.name().toLowerCase(), bookedBy, resId));
        }

        boolean invariantHolds = (availableCount + heldCount + confirmedCount) == show.getTotalSeats();
        if (!invariantHolds) {
            metricsService.recordInvariantViolation(showId);
            log.error("INVARIANT VIOLATION on show {}: available({}) + held({}) + confirmed({}) != total({})",
                    showId, availableCount, heldCount, confirmedCount, show.getTotalSeats());
        }

        return new ShowResponse(
                show.getId(),
                show.getName(),
                show.getPricePaise(),
                show.getPerUserLimit(),
                show.getTotalSeats(),
                availableCount,
                heldCount,
                confirmedCount,
                show.getOwnerUserId(),
                seatDtos
        );
    }

    /**
     * Retrieves all shows ordered by creation date descending.
     */
    @Transactional(readOnly = true)
    public List<ShowResponse> getAllShows() {
        List<Show> shows = showRepository.findAllByOrderByCreatedAtDesc();
        return shows.stream()
                .map(s -> getShowState(s.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Atomically reserves requested seats for an authenticated user with strict race-freedom,
     * deadlock avoidance via deterministic locking order, per-user quota enforcement,
     * and robust idempotency replay.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReserveSeatResponse reserveSeats(String showId, ReserveSeatRequest request, String userId, String headerIdempotencyKey) {
        if (!StringUtils.hasText(userId)) {
            throw new BadRequestException("Authentication required: token-derived user identity is missing");
        }

        String idempotencyKey = StringUtils.hasText(request.getIdempotencyKey())
                ? request.getIdempotencyKey().trim()
                : (StringUtils.hasText(headerIdempotencyKey) ? headerIdempotencyKey.trim() : null);

        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BadRequestException("Missing idempotency key in request body or Idempotency-Key header");
        }

        if (request.getSeats() == null || request.getSeats().isEmpty()) {
            throw new BadRequestException("Requested seats list cannot be empty");
        }

        List<String> requestedSeats = request.getSeats().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());

        if (requestedSeats.isEmpty()) {
            throw new BadRequestException("Requested seats list contained no valid seat identifiers");
        }

        // Canonical hash of the requested payload
        String payloadHash = calculatePayloadHash(showId, requestedSeats);

        // Check for existing idempotency record
        Optional<IdempotencyRecord> existingRecordOpt = idempotencyRecordRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existingRecordOpt.isPresent()) {
            IdempotencyRecord record = existingRecordOpt.get();
            if (!record.getRequestHash().equals(payloadHash)) {
                metricsService.recordReservationDeclined(showId, "idempotency_payload_mismatch");
                log.warn("Idempotency conflict: key='{}' user='{}' reused with different payload", idempotencyKey, userId);
                throw new DomainConflictException("IDEMPOTENCY_PAYLOAD_MISMATCH",
                        "Idempotency key was previously used with a different seat selection or show");
            }
            if (record.getStatus() == IdempotencyStatus.COMPLETED && record.getResponseBody() != null) {
                metricsService.recordIdempotentReplay(showId);
                log.info("Idempotent replay: key='{}' user='{}' returning cached response", idempotencyKey, userId);
                try {
                    return objectMapper.readValue(record.getResponseBody(), ReserveSeatResponse.class);
                } catch (JsonProcessingException e) {
                    log.error("Failed to deserialize cached idempotency response", e);
                }
            } else if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                metricsService.recordReservationDeclined(showId, "concurrent_in_progress");
                throw new DomainConflictException("CONCURRENT_REQUEST_IN_PROGRESS",
                        "A reservation request with this idempotency key is currently executing");
            }
        }

        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ResourceNotFoundException("Show not found with id: " + showId));

        // 1. Sort requested seats lexicographically to guarantee global total lock hierarchy
        List<String> sortedSeatNumbers = new ArrayList<>(requestedSeats);
        Collections.sort(sortedSeatNumbers);

        // 2. Acquire deterministic row-level write locks (SELECT ... FOR UPDATE)
        List<Seat> lockedSeats = seatRepository.findByShowIdAndSeatNumberInWithLock(showId, sortedSeatNumbers);

        if (lockedSeats.size() != sortedSeatNumbers.size()) {
            Set<String> foundSeatNumbers = lockedSeats.stream().map(Seat::getSeatNumber).collect(Collectors.toSet());
            List<String> missingSeats = sortedSeatNumbers.stream().filter(s -> !foundSeatNumbers.contains(s)).collect(Collectors.toList());
            throw new BadRequestException("The following requested seats do not exist: " + missingSeats);
        }

        Instant now = Instant.now();

        // 3. All-or-Nothing check: verify that every requested seat is currently AVAILABLE (or has expired hold)
        List<String> unavailableSeats = new ArrayList<>();
        for (Seat seat : lockedSeats) {
            boolean isHeldAndValid = seat.getStatus() == SeatStatus.HELD
                    && seat.getHeldUntil() != null
                    && seat.getHeldUntil().isAfter(now);

            if (seat.getStatus() == SeatStatus.CONFIRMED || isHeldAndValid) {
                unavailableSeats.add(seat.getSeatNumber());
            }
        }

        if (!unavailableSeats.isEmpty()) {
            metricsService.recordReservationDeclined(showId, "seat_taken");
            log.info("Reservation declined: seats taken {} on show {} for user {}", unavailableSeats, showId, userId);
            throw new DomainConflictException("SEAT_ALREADY_TAKEN",
                    "One or more requested seats are already taken or held", unavailableSeats);
        }

        // 4. Atomic per-user limit check: serialized per (userId, showId)
        synchronized (getUserLock(userId, showId)) {
            int currentActiveSeats = reservationRepository.countActiveSeatsForUser(
                    userId, showId, List.of(ReservationStatus.CONFIRMED, ReservationStatus.HELD));

            if (currentActiveSeats + sortedSeatNumbers.size() > show.getPerUserLimit()) {
                metricsService.recordReservationDeclined(showId, "user_limit_exceeded");
                log.info("Reservation declined: user limit exceeded user={} active={} requested={} limit={}",
                        userId, currentActiveSeats, sortedSeatNumbers.size(), show.getPerUserLimit());
                throw new DomainConflictException("PER_USER_LIMIT_EXCEEDED",
                        String.format("Reservation exceeds per-user limit of %d seats (currently holding %d, requested %d)",
                                show.getPerUserLimit(), currentActiveSeats, sortedSeatNumbers.size()));
            }

            // 5. Create reservation and transition seat states atomically
            long totalAmountPaise = show.getPricePaise() * sortedSeatNumbers.size();
            Reservation reservation = new Reservation(showId, userId, totalAmountPaise, sortedSeatNumbers, idempotencyKey);
            Reservation savedReservation = reservationRepository.saveAndFlush(reservation);

            for (Seat seat : lockedSeats) {
                seat.setStatus(SeatStatus.CONFIRMED);
                seat.setReservationId(savedReservation.getId());
                seat.setHeldUntil(null);
            }
            seatRepository.saveAllAndFlush(lockedSeats);

            ReserveSeatResponse response = new ReserveSeatResponse(
                    savedReservation.getId(),
                    showId,
                    userId,
                    sortedSeatNumbers,
                    totalAmountPaise,
                    savedReservation.getStatus().name().toLowerCase()
            );

            // 6. Record idempotency entry for future exact-replay lookups
            try {
                String serializedResponse = objectMapper.writeValueAsString(response);
                IdempotencyRecord idempotencyRecord = new IdempotencyRecord(idempotencyKey, userId, payloadHash);
                idempotencyRecord.setStatus(IdempotencyStatus.COMPLETED);
                idempotencyRecord.setResponseCode(201);
                idempotencyRecord.setResponseBody(serializedResponse);
                idempotencyRecordRepository.save(idempotencyRecord);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize reservation response for idempotency caching", e);
            } catch (DataIntegrityViolationException e) {
                log.warn("Idempotency key collision caught during record creation: key='{}'", idempotencyKey);
            }

            metricsService.recordReservationConfirmed(showId, sortedSeatNumbers.size());
            log.info("Reservation CONFIRMED: res_id={} show={} user={} seats={} amount={}",
                    savedReservation.getId(), showId, userId, sortedSeatNumbers, totalAmountPaise);

            return response;
        }
    }

    private static final Map<String, Object> USER_SHOW_LOCKS = new ConcurrentHashMap<>();

    private Object getUserLock(String userId, String showId) {
        return USER_SHOW_LOCKS.computeIfAbsent(userId + ":" + showId, k -> new Object());
    }

    /**
     * Cancels an existing reservation and returns its seats cleanly to AVAILABLE state.
     * Allowed for:
     * - The user who made the reservation
     * - System Administrators
     * - The Owner/Manager of the show
     */
    @Transactional
    public CancelReservationResponse cancelReservation(String reservationId, String userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + reservationId));

        Show show = showRepository.findById(reservation.getShowId()).orElse(null);
        boolean isShowOwner = show != null && show.getOwnerUserId() != null && show.getOwnerUserId().equals(userId);

        if (!reservation.getUserId().equals(userId) && !UserContext.isAdmin() && !isShowOwner) {
            throw new ForbiddenException("Cannot cancel a reservation owned by another user unless you are an administrator or the event manager");
        }

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return new CancelReservationResponse(
                    reservation.getId(),
                    reservation.getShowId(),
                    reservation.getUserId(),
                    "cancelled",
                    reservation.getSeats()
            );
        }

        List<String> seatsToRelease = new ArrayList<>(reservation.getSeats());
        Collections.sort(seatsToRelease);

        List<Seat> lockedSeats = seatRepository.findByShowIdAndSeatNumberInWithLock(
                reservation.getShowId(), seatsToRelease);

        for (Seat seat : lockedSeats) {
            if (reservation.getId().equals(seat.getReservationId())) {
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setReservationId(null);
                seat.setHeldUntil(null);
            }
        }
        seatRepository.saveAll(lockedSeats);

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);

        metricsService.recordReservationCancelled(reservation.getShowId(), seatsToRelease.size());

        log.info("Reservation CANCELLED: res_id={} user={} released_seats={}",
                reservationId, userId, seatsToRelease);

        return new CancelReservationResponse(
                reservation.getId(),
                reservation.getShowId(),
                reservation.getUserId(),
                "cancelled",
                seatsToRelease
        );
    }

    /**
     * Background scheduled worker to release expired seat holds.
     */
    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void releaseExpiredHolds() {
        Instant now = Instant.now();
        List<Seat> expiredSeats = seatRepository.findExpiredHoldsWithLock(now);
        if (!expiredSeats.isEmpty()) {
            for (Seat seat : expiredSeats) {
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setHeldUntil(null);
                seat.setReservationId(null);
            }
            seatRepository.saveAll(expiredSeats);
            log.info("Auto-released {} expired seat holds", expiredSeats.size());
        }
    }

    private String calculatePayloadHash(String showId, List<String> seats) {
        List<String> sorted = new ArrayList<>(seats);
        Collections.sort(sorted);
        String raw = showId + ":" + String.join(",", sorted);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }
}
