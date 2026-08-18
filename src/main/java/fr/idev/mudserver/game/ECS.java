package fr.idev.mudserver.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.AbstractObject;

@Service
public class ECS {

    private final Map<UUID, AbstractObject> entities = new ConcurrentHashMap<>();
    private final Map<Class<?>, Map<UUID, Object>> storage = new ConcurrentHashMap<>();

    public void register(AbstractObject entity) {
        entities.put(entity.getId(), entity);
    }

    public void unregister(AbstractObject entity) {
        entities.remove(entity.getId());
        for (Map<UUID, Object> pool : storage.values()) {
            pool.remove(entity.getId());
        }
    }

    public <C> void attach(AbstractObject entity, C component) {
        storage.computeIfAbsent(component.getClass(), type -> new ConcurrentHashMap<>()).put(entity.getId(), component);
    }

    public <C> void detach(AbstractObject entity, Class<C> type) {
        Map<UUID, Object> pool = storage.get(type);
        if (pool != null) {
            pool.remove(entity.getId());
        }
    }

    public <C> Optional<C> find(AbstractObject entity, Class<C> type) {
        Map<UUID, Object> pool = storage.get(type);
        return pool == null ? Optional.empty() : Optional.ofNullable(type.cast(pool.get(entity.getId())));
    }

    public Query createQuery() {
        return new Query();
    }

    public List<QueryResult> execute(Query query) {
        List<Class<?>> requirements = query.requirements();
        if (requirements.isEmpty()) {
            return entities.values().stream().map(entity -> new QueryResult(entity, Map.of())).toList();
        }

        Map<UUID, Object> smallest = null;
        for (Class<?> type : requirements) {
            Map<UUID, Object> pool = storage.getOrDefault(type, Map.of());
            if (pool.isEmpty()) {
                return List.of();
            }
            if (smallest == null || pool.size() < smallest.size()) {
                smallest = pool;
            }
        }

        List<QueryResult> result = new ArrayList<>(smallest.size());
        candidates : for (UUID id : smallest.keySet()) {
            // un component peut être attaché avant que l'entité soit enregistrée (voir
            // constructeurs) : on
            // ignore les entités pas (ou plus) présentes dans le registre pour éviter de
            // renvoyer un id fantôme.
            AbstractObject entity = entities.get(id);
            if (entity == null) {
                continue;
            }
            Map<Class<?>, Object> components = new HashMap<>(requirements.size());
            for (Class<?> type : requirements) {
                Object component = storage.getOrDefault(type, Map.of()).get(id);
                if (component == null) {
                    continue candidates;
                }
                components.put(type, component);
            }
            result.add(new QueryResult(entity, components));
        }
        return result;
    }
}
