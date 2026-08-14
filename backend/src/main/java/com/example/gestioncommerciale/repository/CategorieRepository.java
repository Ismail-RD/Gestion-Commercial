package com.example.gestioncommerciale.repository;

import com.example.gestioncommerciale.entity.Categorie;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategorieRepository extends JpaRepository<Categorie, Long> {
}
