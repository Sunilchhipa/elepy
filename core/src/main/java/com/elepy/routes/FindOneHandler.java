package com.elepy.routes;

import com.elepy.dao.Crud;
import com.elepy.describers.ModelDescription;
import com.elepy.http.HttpContext;
import com.fasterxml.jackson.databind.ObjectMapper;

public interface FindOneHandler<T> {

    /**
     * This handles the functionality of finding a model by it's ID, or some self-defined domain specification
     * <p>
     * e.g GET /model/:id
     *
     * @param crud The crud implementation
     * @throws Exception you can throw any exception and Elepy handles them nicely.
     * @see com.elepy.exceptions.ElepyException
     * @see com.elepy.exceptions.ElepyErrorMessage
     */
    void handleFindOne(HttpContext context, Crud<T> crud, ModelDescription<T> modelDescription, ObjectMapper objectMapper) throws Exception;

}
