package org.tb.khata.login.auth.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository for {@link LoginToken}. Insert-vs-update is handled by {@code save()} based on
 * whether the entity is managed / whether the row already exists at the PK — this is the natural
 * UPSERT for JPA when the PK is client-supplied.
 */
public interface LoginTokenRepository extends JpaRepository<LoginToken, String> {}
