package dong.yoogo.approval.process.domain.adapter;


import dong.yoogo.approval.process.domain.Process;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.stream.Stream;


public interface ProcessRepository extends JpaRepository<Process, String> {

    Stream<Process> findByCreateTimeBefore(LocalDateTime dateTime);
}
