const euroFormatter = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'EUR',
})

// L'API renvoie tous les montants en centimes : on les affiche en euros.
export const centsFormatter = {
    format: (cents: number) => euroFormatter.format(cents / 100),
}
