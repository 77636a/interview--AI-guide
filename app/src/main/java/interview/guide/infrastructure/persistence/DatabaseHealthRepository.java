package interview.guide.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DatabaseHealthRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public boolean isAvailable() {
        Object result = entityManager.createNativeQuery("SELECT 1").getSingleResult();
        return ((Number) result).intValue() == 1;
    }
}

