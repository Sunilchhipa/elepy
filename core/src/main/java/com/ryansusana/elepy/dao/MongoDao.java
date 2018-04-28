package com.ryansusana.elepy.dao;


import com.google.common.collect.Lists;
import com.mongodb.DB;
import com.ryansusana.elepy.annotations.RestModel;
import com.ryansusana.elepy.annotations.Searchable;
import com.ryansusana.elepy.annotations.Unique;
import com.ryansusana.elepy.models.RestErrorMessage;
import com.ryansusana.elepy.utils.ClassUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.jongo.Find;
import org.jongo.Jongo;
import org.jongo.Mapper;
import org.jongo.MongoCollection;
import org.jongo.marshall.jackson.JacksonMapper;
import org.jongo.marshall.jackson.oid.MongoId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.regex.Pattern;

public class MongoDao<T> implements Crud<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoDao.class);
    private final Jongo jongo;
    private final Class<? extends T> classType;
    private final String collectionName;
    private final ObjectMapper objectMapper;


    public MongoDao(final DB db, final String collectionName, final Class<? extends T> classType) {
        this(db, collectionName, JacksonMapper.Builder.jacksonMapper().build(), classType);
    }

    public MongoDao(final DB db, final String collectionName, Mapper objectMapper, final Class<? extends T> classType) {
        this.jongo = new Jongo(db, objectMapper);
        this.objectMapper = new ObjectMapper();
        this.classType = classType;
        this.collectionName = collectionName.replaceAll("/", "");

    }

    protected MongoCollection collection() {
        return jongo.getCollection(collectionName);
    }

    @Override
    public List<T> getAll() {

        return Lists.newArrayList(collection().find().as(classType).iterator());
    }


    @Override
    public Optional<T> getById(final String id) {
        return Optional.ofNullable(collection().findOne("{_id: #}", id).as(classType));
    }


    @Override
    public long count(String query, Object... parameters) {
        return collection().count(query, parameters);
    }


    @Override
    public List<T> search(SearchSetup query) {

        final List<Field> searchableFields = getSearchableFields();
        List<Map<String, String>> expressions = new ArrayList<>();
        Map<String, Object> qmap = new HashMap<>();
        Pattern[] hashs = new Pattern[searchableFields.size()];
        final Pattern pattern = Pattern.compile(".*" + query.getQuery() + ".*", Pattern.CASE_INSENSITIVE);
        for (int i = 0; i < hashs.length; i++) {
            hashs[i] = pattern;
        }
        for (Field field : searchableFields) {
            Map<String, String> keyValue = new HashMap<>();
            keyValue.put(ClassUtils.getPropertyName(field), "#");
            expressions.add(keyValue);
        }
        qmap.put("$or", expressions);
        try {

            Find find = query.getQuery() != null ? collection().find(objectMapper.writeValueAsString(qmap).replaceAll("\"#\"", "#"), (Object[]) hashs) : collection().find();
            List<Field> sortedFields;
            if (query.getSortBy() != null && query.getSortOption() != null) {
                find = find.sort(String.format("{%s: %d}", query.getSortBy(), query.getSortOption().getVal()));
            } else {
                RestModel restModel = classType.getAnnotation(RestModel.class);
                find = find.sort(String.format("{%s: %d}", restModel.defaultSortField(), restModel.defaultSortDirection().getVal()));
            }
            return Lists.newArrayList(find.as(classType).iterator());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RestErrorMessage(e.getMessage());
        }

    }

    @Override
    public List<T> search(String query, Object... params) {
        return Lists.newArrayList(collection().find(query, params).as(classType).iterator());
    }


    private List<Field> getSearchableFields() {
        return ClassUtils.searchForFieldsWithAnnotation(classType, Searchable.class, MongoId.class, Unique.class);
    }


    @Override
    public void delete(String id) {
        collection().remove("{_id: #}", id);
    }

    @Override
    public void update(T item) {

        collection().update("{_id: #}", getId(item)).with(item);

    }

    @Override
    public void create(T item) {
        try {
            final Field idField = ClassUtils.getIdField(item.getClass());
            final RestModel annotation = item.getClass().getAnnotation(RestModel.class);
            final Optional<Constructor<?>> o = ClassUtils.getEmptyConstructor(annotation.idProvider());
            if (!o.isPresent()) {
                throw new IllegalStateException(annotation.idProvider() + " has no empty constructor.");
            }
            final com.ryansusana.elepy.concepts.IdProvider<T> provider = ((Constructor<com.ryansusana.elepy.concepts.IdProvider<T>>) o.get()).newInstance();

            assert idField != null;
            idField.setAccessible(true);
            idField.set(item, provider.getId(this));


            collection().insert(item);
        } catch (IllegalAccessException e) {
            LOGGER.error("Illegal access on Item creation", e);
        } catch (InvocationTargetException | InstantiationException e) {
            e.printStackTrace();
        }

    }

    @Override
    public String getId(T item) {
        Optional<String> id = ClassUtils.getId(item);
        if (!id.isPresent()) {
            throw new IllegalStateException(item.getClass().getName() + ": has no annotation id. You must annotate the class with MongoId and if no id generator is specified, you must generate your own.");
        }
        return id.get();
    }


    public Jongo getJongo() {
        return this.jongo;
    }

    public Class<? extends T> getClassType() {
        return this.classType;
    }

    public String getCollectionName() {
        return this.collectionName;
    }
}
