package com.elepy.evaluators;

import com.elepy.exceptions.ElepyException;
import com.elepy.utils.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AtomicIntegrityEvaluator<T> {
    private static final Logger logger = LoggerFactory.getLogger(AtomicIntegrityEvaluator.class);

    public void evaluate(List<T> items) throws IllegalAccessException {

        for (T item : items) {

            List<T> theRest = new ArrayList<>(items);

            theRest.remove(item);

            checkUniqueness(item, theRest);
        }
    }

    private void checkUniqueness(T item, List<T> theRest) throws IllegalAccessException {
        List<Field> uniqueFields = ClassUtils.getUniqueFields(item.getClass());
        Optional<Object> id = ClassUtils.getId(item);

        for (Field field : uniqueFields) {

            field.setAccessible(true);
            Object prop = field.get(item);


            final List<T> foundItems = theRest.stream().filter(t -> {

                try {
                    return field.get(t).equals(prop);
                } catch (IllegalAccessException e) {
                    logger.error(e.getMessage(), e);
                    return false;
                }
            }).collect(Collectors.toList());

            integrityCheck(foundItems, id, field, prop);
        }
    }

    private void integrityCheck(List<T> foundItems, Optional<Object> id, Field field, Object prop) {
        if (!foundItems.isEmpty()) {


            if (foundItems.size() > 1) {
                throw new ElepyException(String.format("There are duplicates with the %s: '%s' in the given array!", ClassUtils.getPrettyName(field), String.valueOf(prop)));
            }

            T foundRecord = foundItems.get(0);
            final Optional<Object> foundId = ClassUtils.getId(foundRecord);
            if (id.isPresent() || foundId.isPresent()) {
                if (!id.equals(foundId)) {
                    throw new ElepyException(String.format("An item with the %s: '%s' already exists in the system!", ClassUtils.getPrettyName(field), String.valueOf(prop)));
                }
            } else {
                throw new ElepyException(String.format("There are duplicates with the %s: '%s' in the given array!", ClassUtils.getPrettyName(field), String.valueOf(prop)));

            }
        }
    }
}
