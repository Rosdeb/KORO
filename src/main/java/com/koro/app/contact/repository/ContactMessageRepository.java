package com.koro.app.contact.repository;

import com.koro.app.contact.entity.ContactMessage;
import com.koro.app.contact.entity.ContactStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ContactMessageRepository extends MongoRepository<ContactMessage, String> {

    Page<ContactMessage> findByStatus(ContactStatus status, Pageable pageable);

    @Query("{ $or: [ " +
            "{ 'name': { $regex: ?0, $options: 'i' } }, " +
            "{ 'email': { $regex: ?0, $options: 'i' } }, " +
            "{ 'subject': { $regex: ?0, $options: 'i' } }, " +
            "{ 'message': { $regex: ?0, $options: 'i' } } " +
            "] }")
    Page<ContactMessage> search(String query, Pageable pageable);

    @Query("{ 'status': ?1, $or: [ " +
            "{ 'name': { $regex: ?0, $options: 'i' } }, " +
            "{ 'email': { $regex: ?0, $options: 'i' } }, " +
            "{ 'subject': { $regex: ?0, $options: 'i' } }, " +
            "{ 'message': { $regex: ?0, $options: 'i' } } " +
            "] }")
    Page<ContactMessage> searchByStatus(String query, ContactStatus status, Pageable pageable);

    long countByStatus(ContactStatus status);
}
