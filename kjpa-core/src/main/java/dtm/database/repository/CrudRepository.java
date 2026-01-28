package dtm.database.repository;

import dtm.database.annotations.AutoFlush;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CrudRepository<S, ID> {

    S save(S entity);

    @AutoFlush
    S saveAndFlush(S entity);

    List<S> saveAll(Collection<S> entities);



    void delete(S entity);

    @AutoFlush
    void deleteAndFlush(S entity);

    void deleteById(ID id);



    List<S> findAll();

    Optional<S> findById(ID id);

    long count();
}
