package com.cryptocinema.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cryptocinema.dto.ScreeningSeatResponse;
import com.cryptocinema.entity.Role;
import com.cryptocinema.entity.Screening;
import com.cryptocinema.entity.ScreeningSeat;
import com.cryptocinema.entity.ScreeningSeatStatus;
import com.cryptocinema.entity.User;
import com.cryptocinema.exception.ConflictException;
import com.cryptocinema.exception.InvalidCredentialsException;
import com.cryptocinema.exception.ResourceNotFoundException;
import com.cryptocinema.repository.ScreeningRepository;
import com.cryptocinema.repository.ScreeningSeatRepository;
import com.cryptocinema.repository.UserRepository;

@Service
public class ScreeningSeatService {

    private static final int HOLD_MINUTES = 5;

    private final ScreeningSeatRepository screeningSeatRepository;
    private final ScreeningRepository screeningRepository;
    private final ScreeningService screeningService;
    private final UserRepository userRepository;

    public ScreeningSeatService(
            ScreeningSeatRepository screeningSeatRepository,
            ScreeningRepository screeningRepository,
            ScreeningService screeningService,
            UserRepository userRepository
    ) {
        this.screeningSeatRepository = screeningSeatRepository;
        this.screeningRepository = screeningRepository;
        this.screeningService = screeningService;
        this.userRepository = userRepository;
    }

    @Transactional
    public List<ScreeningSeatResponse> findByScreening(Long screeningId, Authentication authentication) {
        Screening screening = getScreening(screeningId);
        screeningService.initializeMissingSeats(screening);
        expireHolds(screeningSeatRepository.findByScreeningId(screeningId), LocalDateTime.now());

        User currentUser = currentUserOrNull(authentication);
        return screeningSeatRepository.findByScreeningIdOrderBySeatRowLabelAscSeatSeatNumberAsc(screeningId).stream()
                .map(screeningSeat -> toResponse(screeningSeat, currentUser))
                .toList();
    }

    @Transactional
    public ScreeningSeatResponse hold(Long screeningId, Long screeningSeatId, Authentication authentication) {
        User currentUser = currentUser(authentication);
        ScreeningSeat screeningSeat = screeningSeatRepository.findByIdAndScreeningIdForUpdate(screeningSeatId, screeningId)
                .orElseThrow(() -> new ResourceNotFoundException("Screening seat not found"));

        expireHoldIfNeeded(screeningSeat, LocalDateTime.now());

        if (screeningSeat.getStatus() != ScreeningSeatStatus.AVAILABLE) {
            throw new ConflictException("This seat is no longer available.");
        }

        screeningSeat.setStatus(ScreeningSeatStatus.HELD);
        screeningSeat.setHeldByUser(currentUser);
        screeningSeat.setHoldExpiresAt(LocalDateTime.now().plusMinutes(HOLD_MINUTES));
        return toResponse(screeningSeat, currentUser);
    }

    @Transactional
    public ScreeningSeatResponse release(Long screeningId, Long screeningSeatId, Authentication authentication) {
        User currentUser = currentUser(authentication);
        ScreeningSeat screeningSeat = screeningSeatRepository.findByIdAndScreeningIdForUpdate(screeningSeatId, screeningId)
                .orElseThrow(() -> new ResourceNotFoundException("Screening seat not found"));

        expireHoldIfNeeded(screeningSeat, LocalDateTime.now());

        if (screeningSeat.getStatus() != ScreeningSeatStatus.HELD || screeningSeat.getHeldByUser() == null) {
            throw new ConflictException("Seat is not held.");
        }

        boolean owner = screeningSeat.getHeldByUser().getId().equals(currentUser.getId());
        if (!owner && currentUser.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Forbidden");
        }

        releaseHold(screeningSeat);
        return toResponse(screeningSeat, currentUser);
    }

    private void expireHolds(List<ScreeningSeat> screeningSeats, LocalDateTime now) {
        screeningSeats.stream()
                .sorted(Comparator.comparing(screeningSeat -> screeningSeat.getSeat().getRowLabel()))
                .forEach(screeningSeat -> expireHoldIfNeeded(screeningSeat, now));
    }

    private void expireHoldIfNeeded(ScreeningSeat screeningSeat, LocalDateTime now) {
        if (screeningSeat.getStatus() == ScreeningSeatStatus.HELD
                && screeningSeat.getHoldExpiresAt() != null
                && screeningSeat.getHoldExpiresAt().isBefore(now)) {
            releaseHold(screeningSeat);
        }
    }

    private void releaseHold(ScreeningSeat screeningSeat) {
        screeningSeat.setStatus(ScreeningSeatStatus.AVAILABLE);
        screeningSeat.setHeldByUser(null);
        screeningSeat.setHoldExpiresAt(null);
    }

    private Screening getScreening(Long screeningId) {
        return screeningRepository.findById(screeningId)
                .orElseThrow(() -> new ResourceNotFoundException("Screening not found"));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InvalidCredentialsException("Invalid authentication");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid authentication"));
    }

    private User currentUserOrNull(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName()).orElse(null);
    }

    private ScreeningSeatResponse toResponse(ScreeningSeat screeningSeat, User currentUser) {
        boolean heldByCurrentUser = currentUser != null
                && screeningSeat.getHeldByUser() != null
                && screeningSeat.getHeldByUser().getId().equals(currentUser.getId());
        return new ScreeningSeatResponse(
                screeningSeat.getId(),
                screeningSeat.getSeat().getId(),
                screeningSeat.getSeat().getRowLabel(),
                screeningSeat.getSeat().getSeatNumber(),
                screeningSeat.getStatus(),
                heldByCurrentUser,
                heldByCurrentUser ? screeningSeat.getHoldExpiresAt() : null);
    }
}
