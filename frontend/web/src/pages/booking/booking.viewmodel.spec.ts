import { createTestStore } from "@/core/create-store";
import { describe, expect, it } from "vitest";
import { selectBookingViewmodel } from "./booking.viewmodel";
import { stateBuilder } from "@/core/state-builder";
import { bookingEntityBuilder } from "@/core/booking/entities/booking.entity";
import { eventEntityBuilder, venueEntityBuilder } from "@/core/events/entities/event.entity";
import { bookingLineEntityBuilder } from "@/core/booking/entities/booking-line.entity";

describe('Booking Viewmodel', () => {
    it('is in loaded state when booking and event are loaded', () => {
        const store = createTestStore({}, stateBuilder().withBooking(bookingEntityBuilder().withEventId('event-1').build()).withEvents([eventEntityBuilder().withId('event-1').build()]).build())
        const viewmodel = selectBookingViewmodel({ bookingId: 'booking-1' })(store.getState())

        expect(viewmodel.type).toBe('loaded')
    })

    it('is in loading state when booking is loading', () => {
        const store = createTestStore({}, stateBuilder().withBookingLoading({ id: 'booking-1', loading: true }).build())
        const viewmodel = selectBookingViewmodel({ bookingId: 'booking-1' })(store.getState())

        expect(viewmodel.type).toBe('loading')
    })

    it('is in loading state when booking event is loading', () => {
        const store = createTestStore({}, stateBuilder().withEventLoading('event-1').withBooking(bookingEntityBuilder().withEventId('event-1').build()).build())
        const viewmodel = selectBookingViewmodel({ bookingId: 'booking-1' })(store.getState())

        expect(viewmodel.type).toBe('loading')
    })

    it('is loaded when booking event is already cached', () => {
        const store = createTestStore({}, stateBuilder()
            .withEventLoading('event-1')
            .withEvents([
                eventEntityBuilder().withId('event-1').build()
            ])
            .withBooking(bookingEntityBuilder()
                .withId('booking-1')
                .withEventId('event-1')
                .build()
            ).build())

        const viewmodel = selectBookingViewmodel({ bookingId: 'booking-1' })(store.getState())

        expect(viewmodel.type).toBe('loaded')
    })

    it('is in expired state when booking is expired', () => {
        const store = createTestStore({}, stateBuilder().withEvents([eventEntityBuilder().withId('event-1').build()]).withBooking(bookingEntityBuilder().withStatus('expired').build()).build())
        const viewmodel = selectBookingViewmodel({ bookingId: 'booking-1' })(store.getState())

        expect(viewmodel.type).toBe('expired')
    })

    it('retrieve eventId when booking is expired', () => {
        const store = createTestStore({}, stateBuilder().withEvents([eventEntityBuilder().withId('event-1').build()]).withBooking(bookingEntityBuilder().withStatus('expired').build()).build())
        const viewmodel = selectBookingViewmodel({ bookingId: 'booking-1' })(store.getState())

        expect(viewmodel).toEqual(expect.objectContaining({
            eventId: 'event-1'
        }))
    })

    it('is in confirmed state when booking is approved', () => {
        const store = createTestStore({}, stateBuilder().withEvents([eventEntityBuilder().withId('event-1').build()]).withBooking(bookingEntityBuilder().withStatus('confirmed').build()).build())
        const viewmodel = selectBookingViewmodel({ bookingId: 'booking-1' })(store.getState())

        expect(viewmodel.type).toBe('confirmed')
    })

    it('retrieve eventId when booking is approved', () => {
        const store = createTestStore({}, stateBuilder().withEvents([eventEntityBuilder().withId('event-1').build()]).withBooking(bookingEntityBuilder().withStatus('confirmed').build()).build())
        const viewmodel = selectBookingViewmodel({ bookingId: 'booking-1' })(store.getState())

        expect(viewmodel).toEqual(expect.objectContaining({
            eventId: 'event-1'
        }))
    })

    it('is in processing state when booking is requested', () => {
        const store = createTestStore({}, stateBuilder().withEvents([eventEntityBuilder().withId('event-1').build()]).withBooking(bookingEntityBuilder().withStatus('requested').build()).build())
        const viewmodel = selectBookingViewmodel({ bookingId: 'booking-1' })(store.getState())

        expect(viewmodel.type).toBe('processing')
    })

    it('is in processing state when booking is reserved', () => {
        const store = createTestStore({}, stateBuilder().withEvents([eventEntityBuilder().withId('event-1').build()]).withBooking(bookingEntityBuilder().withStatus('reserved').build()).build())
        const viewmodel = selectBookingViewmodel({ bookingId: 'booking-1' })(store.getState())

        expect(viewmodel.type).toBe('processing')
    })

    it('is in rejected state when booking is rejected', () => {
        const store = createTestStore({}, stateBuilder().withEvents([eventEntityBuilder().withId('event-1').build()]).withBooking(bookingEntityBuilder().withStatus('rejected').build()).build())
        const viewmodel = selectBookingViewmodel({ bookingId: 'booking-1' })(store.getState())

        expect(viewmodel.type).toBe('rejected')
    })

    it('retrieve eventId when booking is requested', () => {
        const store = createTestStore({}, stateBuilder().withEvents([eventEntityBuilder().withId('event-1').build()]).withBooking(bookingEntityBuilder().withStatus('requested').build()).build())
        const viewmodel = selectBookingViewmodel({ bookingId: 'booking-1' })(store.getState())

        expect(viewmodel).toEqual(expect.objectContaining({
            eventId: 'event-1'
        }))
    })

    it('retrieve eventId when booking is reserved', () => {
        const store = createTestStore({}, stateBuilder().withEvents([eventEntityBuilder().withId('event-1').build()]).withBooking(bookingEntityBuilder().withStatus('reserved').build()).build())
        const viewmodel = selectBookingViewmodel({ bookingId: 'booking-1' })(store.getState())

        expect(viewmodel).toEqual(expect.objectContaining({
            eventId: 'event-1'
        }))
    })

    it('retrieve eventId when booking is rejected', () => {
        const store = createTestStore({}, stateBuilder().withEvents([eventEntityBuilder().withId('event-1').build()]).withBooking(bookingEntityBuilder().withStatus('rejected').build()).build())
        const viewmodel = selectBookingViewmodel({ bookingId: 'booking-1' })(store.getState())

        expect(viewmodel).toEqual(expect.objectContaining({
            eventId: 'event-1'
        }))
    })

    it('retrieve booking details', () => {
        const store = createTestStore({}, stateBuilder()
            .withEventLoading('event-1')
            .withEvents([
                eventEntityBuilder()
                    .withId('event-1')
                    .withTitle('Event 1')
                    .withCover('https://via.placeholder.com/150')
                    .startAt(new Date('2024-12-30T09:30:00'))
                    .withVenue(venueEntityBuilder()
                        .withName('Place')
                        .withAdresse('Adresse')
                        .build()
                    )
                    .build()
            ])
            .withBooking(bookingEntityBuilder()
                .withId('booking-1')
                .withEventId('event-1')
                .withTotal(640)
                .withFees(50)
                .withClientSecret('client-secret-1')
                .withLines([
                    bookingLineEntityBuilder().withId('1').withName('Regular').withPrice(200).withQuantity(2).build(),
                    bookingLineEntityBuilder().withId('2').withName('Simple').withPrice(150).withQuantity(1).build()
                ])
                .build()
            )
            .build())

        const viewmodel = selectBookingViewmodel({ bookingId: 'booking-1' })(store.getState())

        expect(viewmodel).toEqual(expect.objectContaining({
            id: 'booking-1',
            lines: [{
                id: '1',
                name: 'Regular',
                quantity: 2,
                unitPrice: "€2.00",
                total: "€4.00"
            }, {
                id: '2',
                name: 'Simple',
                quantity: 1,
                unitPrice: "€1.50",
                total: "€1.50"
            }],
            event: expect.objectContaining({
                id: 'event-1',
                title: 'Event 1',
                cover: 'https://via.placeholder.com/150',
                time: '09:30 AM',
                date: 'Dec 30, 2024',
                venue: 'Place, Adresse',
            }),
            itemsCount: 3,
            fees: "€0.50",
            total: "€6.40",
            clientSecret: "client-secret-1"
        }))
    })
})
