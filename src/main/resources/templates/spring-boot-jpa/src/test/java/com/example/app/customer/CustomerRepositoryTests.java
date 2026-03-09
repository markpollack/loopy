package com.example.app.customer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CustomerRepositoryTests {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CustomerRepository repository;

    @Test
    void findByLastName() {
        entityManager.persist(new Customer("Alice", "Smith"));
        entityManager.persist(new Customer("Bob", "Jones"));

        var smiths = repository.findByLastName("Smith");

        assertThat(smiths).hasSize(1);
        assertThat(smiths.get(0).getFirstName()).isEqualTo("Alice");
    }

}
