package com.letsplay.api.repository;

import com.letsplay.api.model.Product;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductRepository extends MongoRepository<Product, String> {
    List<Product> findByUserId(String userId);
    void deleteByUserId(String userId);
}
