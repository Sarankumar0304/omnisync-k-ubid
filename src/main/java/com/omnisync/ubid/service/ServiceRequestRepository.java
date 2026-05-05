package com.omnisync.ubid.service;

import com.omnisync.ubid.model.ServiceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, String> {
    List<ServiceRequest> findByUbidOrderByCreatedAtDesc(String ubid);
    Optional<ServiceRequest> findByIdempotencyKey(String idempotencyKey);
    List<ServiceRequest> findByStatus(ServiceRequest.RequestStatus status);
    long countByStatus(ServiceRequest.RequestStatus status);
}
