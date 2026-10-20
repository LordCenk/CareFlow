package com.careflow.queue.repository;

import com.careflow.queue.entity.DoctorDailyQueueSequence;
import com.careflow.queue.entity.DoctorDailyQueueSequence.DoctorDailyQueueSequenceId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DoctorDailyQueueSequenceRepository extends JpaRepository<DoctorDailyQueueSequence, DoctorDailyQueueSequenceId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DoctorDailyQueueSequence s WHERE s.id = :id")
    Optional<DoctorDailyQueueSequence> findByIdWithLock(@Param("id") DoctorDailyQueueSequenceId id);
}
