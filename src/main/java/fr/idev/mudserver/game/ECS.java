package fr.idev.mudserver.game;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import fr.idev.mudserver.domain.actor.AbstractObject;

@Service
public class ECS {

    private final Set<AbstractObject> entities = ConcurrentHashMap.newKeySet();

    public void register(AbstractObject entity) {
        entities.add(entity);
    }

    public void unregister(AbstractObject entity) {
        entities.remove(entity);
    }

    public Query createQuery() {
        return new Query();
    }

    public List<AbstractObject> execute(Query query) {
        return entities.stream().filter(query::matches).toList();
    }
}
