# Backend EventByte

API Spring Boot de la plateforme de billetterie. Elle démarre avec le reste de la stack :

```bash
docker compose up -d
```

L'API écoute sur [http://localhost:3000](http://localhost:3000) (port attendu par le frontend).

## Architecture

```
exposition/   controllers REST, listener RabbitMQ, webhook Stripe     ─┐
service/      orchestration (transactions, appels aux clients)        │ Spring
repository/   accès aux données (Spring Data JPA)                     │
model/        entités JPA                                             │
client/       Strapi, Meilisearch, Stripe (HTTP)                     ─┘
domain/       règles métier en Java pur, sans Spring ni JPA ◄── utilisé par les couches ci-dessus
```

Le package `domain` contient les règles qu'on veut pouvoir tester et faire évoluer sans framework :
frais et total (`BookingPrice`), validité d'une demande (`BookingRules`), stock disponible (`TicketStock`),
échéance de paiement (`BookingExpiration`), sort d'un paiement reçu (`PaymentDecision`),
synchronisation idempotente des tickets Strapi (`TicketSync`), conversion des prix (`Prices`).
Ses tests unitaires tournent en quelques millisecondes ; `DomainIndependenceTest` échoue si une classe du domaine
importe Spring, JPA, Lombok, Jackson ou une couche technique.

## Configuration

Les secrets vont dans le fichier `.env` **à la racine du projet** (ignoré par git), que docker compose transmet au backend.
En local depuis l'IDE, définir les mêmes variables dans la configuration de lancement.

| Variable | Rôle | Où l'obtenir |
|---|---|---|
| `STRAPI_API_TOKEN` | Lire la date des événements dans Strapi (absente du message RabbitMQ) | Strapi admin → Settings → API Tokens → type *Read-only* |
| `STRIPE_SECRET_KEY` | Créer, annuler et rembourser les paiements | Stripe Dashboard (mode test) → Developers → API keys (`sk_test_...`) |
| `STRIPE_WEBHOOK_SECRET` | Vérifier la signature des webhooks Stripe | `stripe listen` (Stripe CLI) ou Dashboard → Webhooks (`whsec_...`) |

> Ne jamais mettre une clé secrète dans `frontend/web/.env` : toute variable `VITE_…` est envoyée au navigateur.
> Le frontend n'a besoin que de la clé publique `pk_test_...` (`VITE_STRIPE_PUBLIC_KEY`).

## Cycle de vie d'une réservation

```
POST /bookings ──► requested ──► reserved ──► awaiting-payment ──► confirmed
                       │            (stock Redis    (PaymentIntent      (webhook Stripe
                       │             atomique)       + clientSecret)     payment_intent.succeeded)
                       ▼                                  │
                   rejected                               ▼ 10 min sans paiement
                (stock insuffisant)                    expired (places remises en vente)
```

- **Stock** : un compteur Redis par ticket, décrémenté atomiquement (script Lua) pour toutes les lignes d'une réservation, ou aucune.
- **Frais** : 30 centimes par ticket, inclus dans `total`. Tous les montants sont en centimes.
- **Délai de paiement** : 10 minutes après `bookedAt` (`BOOKING_EXPIRATION`), comme le compte à rebours du frontend.

## Paiement et webhook Stripe

Le frontend confirme le paiement directement chez Stripe avec le `clientSecret`.
Stripe prévient ensuite le backend sur **`POST /payments/stripe/webhook`** (route publique, signature vérifiée) :
sur `payment_intent.succeeded`, la réservation passe en `confirmed` si le montant payé correspond au total.
Un webhook rejoué est sans effet.

En local, pour recevoir les webhooks :

```bash
stripe listen --forward-to localhost:3000/payments/stripe/webhook
```

La commande affiche le secret `whsec_...` à mettre dans `STRIPE_WEBHOOK_SECRET`, puis redémarrer le backend.

### Paiement arrivé après l'expiration (décision du groupe)

1. À l'expiration d'une réservation, ses places retournent en vente et **son PaymentIntent est annulé chez Stripe** :
   l'utilisateur ne peut plus la payer.
2. Si un paiement aboutit quand même (il était en cours au moment de l'expiration), la réservation **reste `expired`**
   et le paiement est **remboursé intégralement** : les places ont pu être réservées par quelqu'un d'autre entre-temps,
   on ne peut donc plus les garantir.
