package com.example.gestioncommerciale.repository;

import com.example.gestioncommerciale.entity.PouvoirRole;
import com.example.gestioncommerciale.entity.Role;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PouvoirRoleRepository extends JpaRepository<PouvoirRole, Role> {

    List<PouvoirRole> findAll(Sort sort);
}
