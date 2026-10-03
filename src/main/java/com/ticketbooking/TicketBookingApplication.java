package com.ticketbooking;

import com.ticketbooking.dto.CreateShowRequest;
import com.ticketbooking.repository.ShowRepository;
import com.ticketbooking.service.BookingService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
@EnableScheduling
public class TicketBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketBookingApplication.class, args);
    }

    @Bean
    @Profile("!test")
    public CommandLineRunner seedDefaultShow(ShowRepository showRepository, BookingService bookingService) {
        return args -> {
            if (showRepository.count() == 0) {
                List<String> seats = new ArrayList<>();
                String[] rows = {"A", "B", "C", "D", "E", "F"};
                for (String row : rows) {
                    for (int i = 1; i <= 6; i++) {
                        seats.add(row + i);
                    }
                }
                CreateShowRequest request = new CreateShowRequest(
                        "Coldplay: Music of the Spheres World Tour 2026",
                        seats,
                        250000L,
                        4
                );
                bookingService.createShow(request);
            }
        };
    }
}
