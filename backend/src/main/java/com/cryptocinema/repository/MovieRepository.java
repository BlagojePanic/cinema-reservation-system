package com.cryptocinema.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cryptocinema.entity.Movie;

public interface MovieRepository extends JpaRepository<Movie, Long> {
}
