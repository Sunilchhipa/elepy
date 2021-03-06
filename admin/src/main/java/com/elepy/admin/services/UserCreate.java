package com.elepy.admin.services;

import com.elepy.admin.ElepyAdminPanel;
import com.elepy.admin.models.User;
import com.elepy.admin.models.UserType;
import com.elepy.annotations.Inject;
import com.elepy.dao.Crud;
import com.elepy.describers.ModelDescription;
import com.elepy.evaluators.DefaultIntegrityEvaluator;
import com.elepy.evaluators.ObjectEvaluator;
import com.elepy.exceptions.ElepyException;
import com.elepy.http.Filter;
import com.elepy.http.HttpContext;
import com.elepy.routes.CreateHandler;
import com.fasterxml.jackson.databind.ObjectMapper;


public class UserCreate implements CreateHandler<User> {

    @Inject(tag = "protected")
    private Filter allAdminFilter;

    @Override
    public void handleCreate(HttpContext context, Crud<User> crud, ModelDescription<User> modelDescription, ObjectMapper objectMapper) throws Exception {


        String body = context.request().body();
        User user = objectMapper.readValue(body, crud.getType());
        if (user.getUsername().trim().isEmpty()) {
            throw new ElepyException("Usernames can't be empty!", 400);
        }
        if (crud.count() > 0) {
            allAdminFilter.authenticate(context);
            User loggedInUser = context.request().attribute(ElepyAdminPanel.ADMIN_USER);


            for (ObjectEvaluator<User> objectEvaluator : modelDescription.getObjectEvaluators()) {
                objectEvaluator.evaluate(user, User.class);
            }
            new DefaultIntegrityEvaluator<User>().evaluate(user, crud, true);

            if (!loggedInUser.getUserType().hasMoreRightsThan(user.getUserType())) {
                throw new ElepyException("You are not allowed to create users with an equal higher rank than you!");
            }
            user = user.hashWord();
            crud.create(user);
            context.response().result(context.request().body());
        } else {


            for (ObjectEvaluator<User> objectEvaluator : modelDescription.getObjectEvaluators()) {
                objectEvaluator.evaluate(user, User.class);
            }
            new DefaultIntegrityEvaluator<User>().evaluate(user, crud, true);

            user.setEmail("");
            user.setUserType(UserType.SUPER_ADMIN);

            if (user.getPassword().length() < 5) {
                throw new ElepyException("Passwords must be more than 4 characters long!", 400);
            }
            crud.create(user.hashWord());
            context.response().result("Successfully created the user");

        }


    }
}
