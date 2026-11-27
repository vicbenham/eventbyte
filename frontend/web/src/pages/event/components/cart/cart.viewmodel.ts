import { centsFormatter } from "@/lib/price-formatter";
import { bookCart } from "@/core/booking/usecases/book-cart/book-cart";
import { selectCartItems } from "@/core/cart/cart.slice";
import { addToCart } from "@/core/cart/usecases/add-ticket-to-cart/add-ticket-to-cart";
import { removeFromCart } from "@/core/cart/usecases/remove-ticket-from-cart/remove-ticket-from-cart";
import { AppDispatch, RootState } from "@/core/create-store";
import { selectAllTickets } from "@/core/tickets/tickets.slice";
import { createSelector } from "@reduxjs/toolkit";

type Line = {
    id: string
    name: string
    quantity: number
    unitPrice: string
    total: string
}

type CartViewModel = {
    itemsCount: number
    lines: Line[]
    fees: string
    total: string
    add: (id: string) => void
    remove: (id: string) => void
    book: () => Promise<void>
}

export type SelectCartViewmodelProps = {
    dispatch: AppDispatch
    onBookSuccess: (id: string) => void
}

export const selectCartViewmodel = ({ dispatch, onBookSuccess }: SelectCartViewmodelProps): (state: RootState) => CartViewModel => createSelector([selectCartItems, selectAllTickets], (items, tickets) => {
    const priceFormater = centsFormatter

    const lines = items.reduce((acc, item) => {
        const ticket = tickets.find(ticket => ticket.id === item.id)
        const price = ticket?.price || 0
        const total = item.quantity * price
        return [...acc, {
            id: item.id,
            name: ticket?.name || '',
            quantity: item.quantity,
            unitPrice: priceFormater.format(price),
            total: priceFormater.format(total)
        }]
    }, [] as Line[])

    const totalQuantity = items.reduce((acc, item) => acc + item.quantity, 0)
    const fees = totalQuantity * 30 // 30 centimes par ticket

    const total = lines.reduce((acc, item) => {
        const ticket = tickets.find(ticket => ticket.id === item.id)
        const quantity = item.quantity || 0
        const price = ticket?.price || 0
        return acc + quantity * price
    }, fees)

    const add = (id: string) => {
        dispatch(addToCart(id))
    }

    const remove = (id: string) => {
        dispatch(removeFromCart(id))
    }

    const book = async () => {
        const id = await dispatch(bookCart()).unwrap()
        onBookSuccess(id)
    }

    return {
        itemsCount: totalQuantity,
        lines,
        fees: priceFormater.format(fees),
        total: priceFormater.format(total),
        add,
        remove,
        book
    }
})
