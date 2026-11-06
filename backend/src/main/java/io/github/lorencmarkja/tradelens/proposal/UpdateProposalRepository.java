package io.github.lorencmarkja.tradelens.proposal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UpdateProposalRepository extends JpaRepository<UpdateProposal, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from UpdateProposal p where p.id = :id")
    Optional<UpdateProposal> findForUpdate(@Param("id") UUID id);
}
