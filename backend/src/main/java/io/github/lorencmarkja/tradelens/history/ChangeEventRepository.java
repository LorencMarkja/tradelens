package io.github.lorencmarkja.tradelens.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChangeEventRepository extends JpaRepository<ChangeEvent, Long> {

    List<ChangeEvent> findAllByOrderByOccurredAtDescIdDesc();

    Optional<ChangeEvent> findByProposalId(UUID proposalId);
}
