package com.elepy.utils;

import com.elepy.annotations.Identifier;
import com.elepy.annotations.PrettyName;
import com.elepy.annotations.Unique;
import com.elepy.exceptions.ElepyConfigException;
import com.elepy.exceptions.ElepyException;
import com.elepy.http.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jongo.marshall.jackson.oid.MongoId;

import javax.persistence.Column;
import javax.persistence.Id;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.elepy.http.RouteBuilder.anElepyRoute;

public class ClassUtils {

    private ClassUtils() {
    }

    @SafeVarargs
    public static List<Field> searchForFieldsWithAnnotation(Class cls, Class<? extends Annotation>... annotations) {
        List<Field> fields = new ArrayList<>();
        for (Field field : cls.getDeclaredFields()) {
            field.setAccessible(true);
            for (Class<? extends Annotation> annotation : annotations) {
                if (field.isAnnotationPresent(annotation)) {
                    fields.add(field);
                    break;
                }
            }

        }
        return fields;
    }

    @SafeVarargs
    public static Optional<Field> searchForFieldWithAnnotation(Class cls, Class<? extends Annotation>... annotations) {
        return searchForFieldsWithAnnotation(cls, annotations).stream().findFirst();
    }

    public static String getPropertyName(Field field) {
        if (field.isAnnotationPresent(JsonProperty.class)) {
            return field.getAnnotation(JsonProperty.class).value();

        } else if (field.isAnnotationPresent(Column.class)) {
            return field.getAnnotation(Column.class).columnDefinition();
        } else if (hasId(field)) {
            return "id";
        } else {
            return field.getName();
        }
    }

    public static Field getPropertyField(Class<?> cls, String property) {
        for (Field declaredField : cls.getDeclaredFields()) {
            declaredField.setAccessible(true);
            if (getPropertyName(declaredField).equals(property)) {
                return declaredField;
            }
        }
        return null;
    }

    public static String getPrettyName(Field field) {
        if (field.isAnnotationPresent(PrettyName.class)) {
            return field.getAnnotation(PrettyName.class).value();
        }
        return getPropertyName(field);
    }

    private static boolean hasId(Field field) {
        return field.isAnnotationPresent(MongoId.class) || field.isAnnotationPresent(Identifier.class) || field.isAnnotationPresent(Id.class);
    }

    public static Optional<Object> getId(Object object) {

        try {
            Field field = getIdField(object.getClass()).orElseThrow(() -> new ElepyException("No ID field found"));
            field.setAccessible(true);
            return Optional.ofNullable(field.get(object));
        } catch (IllegalAccessException e) {
            throw new ElepyException("Illegally accessing id field");
        }

    }


    public static Object toObject(Class clazz, String value) {
        if (Boolean.class == clazz || boolean.class == clazz) return Boolean.valueOf(value);
        if (Byte.class == clazz || byte.class == clazz) return Byte.valueOf(value);
        if (Short.class == clazz || short.class == clazz) return Short.valueOf(value);
        if (Integer.class == clazz || int.class == clazz) return Integer.valueOf(value);
        if (Long.class == clazz || long.class == clazz) return Long.valueOf(value);
        if (Float.class == clazz || float.class == clazz) return Float.valueOf(value);
        if (Double.class == clazz || double.class == clazz) return Double.valueOf(value);
        return value;
    }

    public static Object toObjectIdFromString(Class tClass, String value) {
        Class<?> idType = ClassUtils.getIdField(tClass).orElseThrow(() -> new ElepyException("Can't findMany the ID field", 500)).getType();

        return toObject(idType, value);
    }

    public static Optional<Field> getIdField(Class cls) {
        Optional<Field> annotated = searchForFieldWithAnnotation(cls, Identifier.class, MongoId.class, Id.class);

        if (annotated.isPresent()) {
            return annotated;
        } else {
            return findFieldWithName(cls, "id");
        }
    }

    public static Optional<Field> findFieldWithName(Class cls, String name) {
        for (Field field : cls.getDeclaredFields()) {
            if (field.isAnnotationPresent(JsonProperty.class)) {
                final JsonProperty annotation = field.getAnnotation(JsonProperty.class);

                if (annotation.value().equals(name)) {
                    return Optional.of(field);
                }
            } else {
                if (field.getName().equals(name)) {
                    return Optional.of(field);
                }
            }
        }
        return Optional.empty();
    }

    public static <T> Optional<Constructor<? extends T>> getEmptyConstructor(Class<?> cls) {
        return getConstructor(cls, 0);
    }

    public static <T> Optional<Constructor<? extends T>> getConstructor(Class<?> cls, int amountOfParams) {
        for (Constructor constructor : cls.getDeclaredConstructors()) {
            constructor.setAccessible(true);
            if (constructor.getParameterCount() == amountOfParams) {
                return Optional.of((Constructor<T>) constructor);
            }
        }

        return Optional.empty();
    }

    public static boolean hasIntegrityRules(Class<?> cls) {
        final List<Field> fields = searchForFieldsWithAnnotation(cls, Unique.class);


        return !fields.isEmpty() || hasJPAIntegrityRules(cls);
    }

    private static boolean hasJPAIntegrityRules(Class<?> cls) {
        final List<Field> fields = searchForFieldsWithAnnotation(cls, Column.class);

        for (Field field : fields) {
            final Column unique = field.getAnnotation(Column.class);
            if (unique.unique()) {
                return true;
            }
        }
        return false;
    }

    public static List<Field> getUniqueFields(Class cls) {
        List<Field> uniqueFields = searchForFieldsWithAnnotation(cls, Unique.class);
        uniqueFields.addAll(searchForFieldsWithAnnotation(cls, Column.class).stream().filter(field -> {
            final Column column = field.getAnnotation(Column.class);
            return column.unique();
        }).collect(Collectors.toList()));

        getIdField(cls).ifPresent(uniqueFields::add);

        return uniqueFields;
    }


    public static Route routeFromMethod(Object obj, Method method) {
        com.elepy.annotations.Route annotation = method.getAnnotation(com.elepy.annotations.Route.class);
        HttpContextHandler route;
        if (method.getParameterCount() == 0) {
            route = ctx -> {
                Object invoke = method.invoke(obj);
                if (invoke instanceof String) {
                    ctx.response().result((String) invoke);
                }
            };
        } else if (method.getParameterCount() == 2
                && method.getParameterTypes()[0].equals(Request.class)
                && method.getParameterTypes()[1].equals(Response.class)) {

            route = ctx -> {
                Object invoke = method.invoke(obj, ctx.request(), ctx.response());
                if (invoke instanceof String) {
                    ctx.response().result((String) invoke);
                }
            };

        } else if (method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(HttpContext.class)) {
            route = ctx -> {
                Object invoke = method.invoke(obj, ctx);
                if (invoke instanceof String) {
                    ctx.response().result((String) invoke);
                }
            };
        } else {
            throw new ElepyConfigException("@HttpContextHandler annotated method must have no parameters or (Request, Response)");
        }
        return anElepyRoute()
                .accessLevel(annotation.accessLevel())
                .path(annotation.path())
                .method(annotation.requestMethod())
                .route(route)
                .build();
    }

    public static List<Route> scanForRoutes(Object obj) {
        List<Route> toReturn = new ArrayList<>();
        for (Method method : obj.getClass().getDeclaredMethods()) {
            method.setAccessible(true);
            if (method.isAnnotationPresent(com.elepy.annotations.Route.class)) {
                toReturn.add(routeFromMethod(obj, method));
            }
        }
        return toReturn;
    }

}
