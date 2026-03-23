package com.psiconet.services.interfaces.profile;

import com.psiconet.model.entities.access.User;

public interface UserService {
    Object getLoggedUserProfile(User user);
}
