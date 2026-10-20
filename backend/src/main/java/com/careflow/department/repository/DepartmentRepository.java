package com.careflow.department.repository;

import com.careflow.department.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByName(String name);
    boolean existsByName(String name);
    List<Department> findByActiveTrue();
}
