package io.github.lorencmarkja.tradelens.scenario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    List<Offer> findByLotCodeOrderByIdAsc(String lotCode);
}
