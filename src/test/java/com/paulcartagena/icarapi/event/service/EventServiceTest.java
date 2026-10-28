package com.paulcartagena.icarapi.event.service;

import com.paulcartagena.icarapi.event.dto.EventPhotoRequest;
import com.paulcartagena.icarapi.event.dto.EventPhotoResponse;
import com.paulcartagena.icarapi.event.dto.EventRequest;
import com.paulcartagena.icarapi.event.dto.EventResponse;
import com.paulcartagena.icarapi.event.entity.Event;
import com.paulcartagena.icarapi.event.entity.EventPhoto;
import com.paulcartagena.icarapi.event.repository.EventPhotoRepository;
import com.paulcartagena.icarapi.event.repository.EventRepository;
import com.paulcartagena.icarapi.exception.ApiException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 12, 24, 19, 0);
    private static final LocalDateTime END = START.plusHours(2);

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventPhotoRepository eventPhotoRepository;

    @InjectMocks
    private EventService eventService;

    @Nested
    class CreateEvent {

        @Test
        void createsActiveEventWithTrimmedFieldsAndBlankLocationAsNull() {
            when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

            EventResponse response = eventService.createEvent(
                    new EventRequest(" Culto de Navidad ", " Celebración ", START, END, "  "));

            assertThat(response.title()).isEqualTo("Culto de Navidad");
            assertThat(response.description()).isEqualTo("Celebración");
            assertThat(response.location()).isNull();
            assertThat(response.active()).isTrue();
            assertThat(response.photos()).isEmpty();
        }

        @Test
        void allowsEventThatEndsWhenItStarts() {
            when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

            EventResponse response = eventService.createEvent(new EventRequest("Culto", "Culto", START, START, null));

            assertThat(response.endAt()).isEqualTo(START);
        }

        @Test
        void rejectsEventThatEndsBeforeItStarts() {
            assertApiException(() -> eventService.createEvent(
                    new EventRequest("Culto", "Culto", START, START.minusMinutes(1), null)), HttpStatus.BAD_REQUEST);
            verify(eventRepository, never()).save(any());
        }
    }

    @Nested
    class UpdateEvent {

        @Test
        void rejectsEventThatEndsBeforeItStarts() {
            Event event = event(true);
            when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

            assertApiException(() -> eventService.updateEvent(event.getId(),
                    new EventRequest("Otro", "Otro", END, START, null)), HttpStatus.BAD_REQUEST);
            assertThat(event.getTitle()).isEqualTo("Culto");
        }

        @Test
        void missingEventReturns404() {
            UUID id = UUID.randomUUID();
            when(eventRepository.findById(id)).thenReturn(Optional.empty());

            assertApiException(() -> eventService.updateEvent(id,
                    new EventRequest("Culto", "Culto", START, END, null)), HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class ActiveFlag {

        @Test
        void deactivateHidesEventAndIsIdempotent() {
            Event event = event(true);
            when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

            eventService.deactivateEvent(event.getId());
            EventResponse response = eventService.deactivateEvent(event.getId());

            assertThat(response.active()).isFalse();
        }
    }

    @Nested
    class Lists {

        @Test
        void requiresBothBoundsOfTheRange() {
            assertApiException(() -> eventService.getAllEvents(null, LocalDate.now(), null), HttpStatus.BAD_REQUEST);
            assertApiException(() -> eventService.getPublicEvents(null, LocalDate.now()), HttpStatus.BAD_REQUEST);
            verifyNoInteractions(eventRepository);
        }

        @Test
        void rejectsRangeWhereFromIsAfterTo() {
            LocalDate today = LocalDate.now();

            assertApiException(() -> eventService.getPublicEvents(today, today.minusDays(1)), HttpStatus.BAD_REQUEST);
            verifyNoInteractions(eventRepository);
        }
    }

    @Nested
    class PublicEvent {

        @Test
        void returnsActiveEvent() {
            Event event = event(true);
            when(eventRepository.findByIdAndActiveTrue(event.getId())).thenReturn(Optional.of(event));

            assertThat(eventService.getPublicEventById(event.getId()).id()).isEqualTo(event.getId());
        }

        @Test
        void inactiveOrMissingEventReturns404() {
            UUID id = UUID.randomUUID();
            when(eventRepository.findByIdAndActiveTrue(id)).thenReturn(Optional.empty());

            assertApiException(() -> eventService.getPublicEventById(id), HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class Photos {

        @Test
        void newPhotoGoesLastWhenNoOrderIsGiven() {
            Event event = event(true);
            event.getPhotos().add(photo(event, 3));
            when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
            when(eventPhotoRepository.save(any(EventPhoto.class))).thenAnswer(invocation -> invocation.getArgument(0));

            EventPhotoResponse response = eventService.addPhoto(event.getId(),
                    new EventPhotoRequest(" https://cdn.icar.org/1.jpg ", null));

            assertThat(response.displayOrder()).isEqualTo(4);
            assertThat(response.imageUrl()).isEqualTo("https://cdn.icar.org/1.jpg");
            assertThat(event.getPhotos()).hasSize(2);
        }

        @Test
        void firstPhotoGetsOrderOne() {
            Event event = event(true);
            when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
            when(eventPhotoRepository.save(any(EventPhoto.class))).thenAnswer(invocation -> invocation.getArgument(0));

            EventPhotoResponse response = eventService.addPhoto(event.getId(),
                    new EventPhotoRequest("https://cdn.icar.org/1.jpg", null));

            assertThat(response.displayOrder()).isEqualTo(1);
        }

        @Test
        void updateKeepsOrderWhenNoneIsGiven() {
            Event event = event(true);
            EventPhoto photo = photo(event, 2);
            when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
            when(eventPhotoRepository.findByIdAndEventId(photo.getId(), event.getId())).thenReturn(Optional.of(photo));

            EventPhotoResponse response = eventService.updatePhoto(event.getId(), photo.getId(),
                    new EventPhotoRequest("https://cdn.icar.org/2.jpg", null));

            assertThat(response.displayOrder()).isEqualTo(2);
            assertThat(response.imageUrl()).isEqualTo("https://cdn.icar.org/2.jpg");
        }

        @Test
        void photoOfAnotherEventReturns404() {
            Event event = event(true);
            UUID photoId = UUID.randomUUID();
            when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
            when(eventPhotoRepository.findByIdAndEventId(photoId, event.getId())).thenReturn(Optional.empty());

            assertApiException(() -> eventService.deletePhoto(event.getId(), photoId), HttpStatus.NOT_FOUND);
            verify(eventPhotoRepository, never()).delete(any());
        }

        @Test
        void eventResponseListsPhotosByDisplayOrder() {
            Event event = event(true);
            event.getPhotos().add(photo(event, 2));
            event.getPhotos().add(photo(event, 1));
            when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

            EventResponse response = eventService.getEventById(event.getId());

            assertThat(response.photos()).extracting(EventPhotoResponse::displayOrder).containsExactly(1, 2);
        }
    }

    private static void assertApiException(ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(status);
    }

    private static Event event(boolean active) {
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setTitle("Culto");
        event.setDescription("Culto dominical");
        event.setStartAt(START);
        event.setEndAt(END);
        event.setActive(active);
        return event;
    }

    private static EventPhoto photo(Event event, int displayOrder) {
        EventPhoto photo = new EventPhoto();
        photo.setId(UUID.randomUUID());
        photo.setEvent(event);
        photo.setImageUrl("https://cdn.icar.org/" + displayOrder + ".jpg");
        photo.setDisplayOrder(displayOrder);
        return photo;
    }
}
