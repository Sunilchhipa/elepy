package com.elepy.dao;

import com.elepy.BaseFongo;
import com.elepy.concepts.Resource;
import com.elepy.dao.jongo.DefaultMongoDao;
import org.jongo.Jongo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultMongoDaoTest extends BaseFongo {

    private DefaultMongoDao<Resource> defaultMongoDao;
    private Jongo jongo;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        defaultMongoDao = new DefaultMongoDao<>(getDb(), "resources", Resource.class);
        jongo = new Jongo(getDb());
    }

    @Test
    public void testCreate() {

        defaultMongoDao.create(validObject());
        defaultMongoDao.create(validObject());

        final long resources = jongo.getCollection("resources").count();

        assertEquals(2, resources);
    }

    @Test
    public void testDelete() {
        final Resource resource = validObject();
        defaultMongoDao.create(resource);
        defaultMongoDao.delete(resource.getId());
        assertEquals(0, count());
    }


    @Test
    public void testSearch() {

        final Resource resource = validObject();
        defaultMongoDao.create(resource);


        final Page<Resource> searchable = defaultMongoDao.search(new QuerySetup("searchable", null, null, 1L, 1));
        assertEquals(1, searchable.getValues().size());

    }

    @Test
    public void testCountSearch() {

        final Resource resource = validObject();
        defaultMongoDao.create(resource);


        final long searchable = defaultMongoDao.count("searchable");
        assertEquals(1, searchable);

    }

    @Test
    public void testMultiCreate() {
        final Resource resource = validObject();
        final Resource resource2 = validObject();

        resource2.setUnique("Unique2");

        defaultMongoDao.create(Arrays.asList(resource, resource2));

        assertEquals(2, count());


    }

    public void testGetById() {

    }

    private long count() {
        return jongo.getCollection("resources").count();
    }
}