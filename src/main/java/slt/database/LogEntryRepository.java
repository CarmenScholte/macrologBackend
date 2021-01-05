package slt.database;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import slt.database.entities.LogEntry;

import javax.transaction.Transactional;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.List;

interface LogEntryCrudRepository extends CrudRepository<LogEntry, Long> {
    void deleteByUserIdAndId(Integer userId, Long logEntryId);

    void deleteByUserId(Integer userId);

    List<LogEntry> findByUserIdAndFoodIdAndDayAndMeal(Integer userId, Long foodId, Date day, String meal);

    List<LogEntry> findByUserId(Integer userId);

    List<LogEntry> findByUserIdAndDay(Integer userId, Date date);

    List<LogEntry> findByUserIdAndDayAndMeal(Integer userId, Date date, String meal);

    @Query("select l from LogEntry l where l.userId = :userId and l.day >= :begin and l.day <= :end")
    List<LogEntry> findByUserIdWithDayAfterAndDayBefore(@Param("userId") Integer userId,
                                                        @Param("begin") Date begin,
                                                        @Param("end") Date end);
}


@Repository
@Slf4j
public class LogEntryRepository {

    @Autowired
    private LogEntryCrudRepository logEntryCrudRepository;

    public void saveLogEntry(Integer userId, LogEntry entry) {
        entry.setUserId(userId);
        logEntryCrudRepository.save(entry);
    }
    
    @Transactional
    public void deleteLogEntry(Integer userId, Long logEntryId) {
        logEntryCrudRepository.deleteByUserIdAndId(userId, logEntryId);
    }

    @Transactional
    public void deleteAllForUser(Integer userId) {
        logEntryCrudRepository.deleteByUserId(userId);
    }

    public List<LogEntry> getLogEntry(Integer userId, Long foodId, Date day, String meal) {
        return logEntryCrudRepository.findByUserIdAndFoodIdAndDayAndMeal(userId, foodId, day, meal);
    }

    public List<LogEntry> getAllLogEntries(Integer userId) {
        return logEntryCrudRepository.findByUserId(userId);
    }

    public List<LogEntry> getAllLogEntries(Integer userId, LocalDate date) {
        log.debug("Getting entries for " + date);
        return logEntryCrudRepository.findByUserIdAndDay(userId, Date.valueOf(date));
    }

    public List<LogEntry> getAllLogEntries(Integer userId, LocalDate date, String meal) {
        log.debug("Getting entries for " + date + " and " + meal);
        return logEntryCrudRepository.findByUserIdAndDayAndMeal(userId, Date.valueOf(date), meal);
    }

    public List<LogEntry> getAllLogEntries(Integer userId, Date begin, Date end) {
        log.debug("Getting entries for period " + begin + " - " + end);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        log.debug("BETWEEN " + sdf.format(begin) + " AND " + sdf.format(end));
        return logEntryCrudRepository.findByUserIdWithDayAfterAndDayBefore(userId,begin,end);
    }
}