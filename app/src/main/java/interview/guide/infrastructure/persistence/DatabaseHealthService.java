package interview.guide.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DatabaseHealthService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthService.class);

    private final DatabaseHealthRepository databaseHealthRepository;

    public DatabaseHealthService(DatabaseHealthRepository databaseHealthRepository) {
        this.databaseHealthRepository = databaseHealthRepository;
    }

    public DatabaseStatus check() {
        try {
            return databaseHealthRepository.isAvailable() ? DatabaseStatus.up() : DatabaseStatus.down();
        } catch (RuntimeException exception) {
            log.warn("Database health check failed: {}", exception.getClass().getSimpleName());
            return DatabaseStatus.down();
        }
    }
}

