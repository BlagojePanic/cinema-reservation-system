package com.cryptocinema.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.cryptocinema.entity.Movie;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    @Query("select movie from Movie movie where movie.status is null or movie.status = com.cryptocinema.entity.MovieStatus.ACTIVE")
    List<Movie> findActiveMovies();
}
