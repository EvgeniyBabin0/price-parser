package com.example.priceparser.repository;

import com.example.priceparser.model.TrackedUrl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrackedUrlRepository extends JpaRepository<TrackedUrl, Long> {

    Optional<TrackedUrl> findByUrl(String url);

    boolean existsByUrl(String url);
}
