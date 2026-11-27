import { centsFormatter } from "@/lib/price-formatter";
import { Button } from "@/design-system/components/ui/button";
import { Card, CardContent, CardHeader } from "@/design-system/components/ui/card";
import { Skeleton } from "@/design-system/components/ui/skeleton";
import { withLoading } from "@/lib/with-loading";
import { Minus, Plus } from "@/components/icons";
import { FC } from "react";
import clsx from "clsx";
import { useSelector } from "react-redux";
import { selectEventTicketsIds, selectIsLoadingEventTickets, selectTicketById } from "@/core/tickets/tickets.slice";
import { useAppDispatch } from "@/hooks/store-hooks";
import { addToCart } from "@/core/cart/usecases/add-ticket-to-cart/add-ticket-to-cart";
import { removeFromCart } from "@/core/cart/usecases/remove-ticket-from-cart/remove-ticket-from-cart";
import { selectCartIsEmpty, selectCartItemQuantity } from "@/core/cart/cart.slice";
import { Cart } from "./cart/cart";

type Props = {
    eventId: string
}

const EventTickets: FC<Props> = ({ eventId }) => {
    const tickets = useSelector(selectEventTicketsIds(eventId))
    const isLoading = useSelector(selectIsLoadingEventTickets(eventId))
    const cartIsEmpty = useSelector(selectCartIsEmpty)
    const displayCart = !cartIsEmpty

    if (isLoading) {
        return <Loading />
    }

    return (
        <section id="tickets" className="flex gap-4">
            <div className="space-y-4 flex-2">
                <h2 className="text-2xl font-bold">Tickets</h2>
                <div className="space-y-4 max-w-4xl">
                    {
                        tickets.map((id) => (
                            <TicketCard key={id} ticketId={id} />
                        ))
                    }
                </div>
            </div>
            {displayCart && <Cart />}
        </section>
    )
}

type TicketCardProps = {
    ticketId: string
}

const TicketCard: FC<TicketCardProps> = ({ ticketId: id }) => {
    const dispatch = useAppDispatch();
    const ticket = useSelector(selectTicketById(id));
    const quantity = useSelector(selectCartItemQuantity(id));

    if (!ticket) {
        return null;
    }

    const { name, price, quantity: maxQuantity } = ticket

    const soldOut = maxQuantity === 0


    const add = () => {
        dispatch(addToCart(id))
    }

    const remove = () => {
        dispatch(removeFromCart(id))
    }

    return (
        <Card className={clsx("gap-1", !soldOut && "bg-foreground text-background", soldOut && "text-muted-foreground")}>
            <CardHeader>
                <h3 className="text-lg uppercase">{name}</h3>
            </CardHeader>
            <CardContent className="flex items-center justify-between">
                <span>{centsFormatter.format(price)}</span>
                {soldOut && <Button variant="outline" disabled className="text-base uppercase">Sold out</Button>}
                {!soldOut && (
                    <div className="flex items-center gap-4">
                        <Button size="icon" className="rounded-full" onClick={remove}><Minus /></Button>
                        <span className="text-2xl font-bold w-8 text-center">{quantity}</span>
                        <Button size="icon" className="rounded-full" onClick={add}><Plus /></Button>
                    </div>
                )}
            </CardContent>
        </Card>
    )
}

const Loading: FC = () => {
    return (
        <section className="space-y-4">
            <Skeleton className="h-10 w-48" />
            <div className="space-y-4 max-w-4xl">
                {Array.from({ length: 3 }).map((_, index) => (
                    <Card key={index} className="gap-2">
                        <CardHeader>
                            <Skeleton className="h-6 w-32" />
                        </CardHeader>
                        <CardContent className="flex items-center justify-between">
                            <Skeleton className="h-6 w-20" />
                            <div className="flex items-center gap-4">
                                <Skeleton className="h-10 w-10 rounded-full" />
                                <Skeleton className="h-8 w-8" />
                                <Skeleton className="h-10 w-10 rounded-full" />
                            </div>
                        </CardContent>
                    </Card>
                ))}
            </div>
        </section>
    )
}

export default withLoading(EventTickets, Loading)
