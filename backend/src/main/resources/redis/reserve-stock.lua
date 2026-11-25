-- Réserve atomiquement les places de toutes les lignes d'une réservation, ou aucune.
-- KEYS[i] : compteur de places disponibles d'un ticket ; ARGV[i] : quantité demandée.
-- Redis exécute le script d'un bloc : aucune autre réservation ne peut s'intercaler.
-- Retourne 1 si tout est réservé, 0 si une ligne manque de stock (rien n'est alors décrémenté).
for i = 1, #KEYS do
    local available = tonumber(redis.call('GET', KEYS[i]) or '-1')
    if available < tonumber(ARGV[i]) then
        return 0
    end
end
for i = 1, #KEYS do
    redis.call('DECRBY', KEYS[i], ARGV[i])
end
return 1
