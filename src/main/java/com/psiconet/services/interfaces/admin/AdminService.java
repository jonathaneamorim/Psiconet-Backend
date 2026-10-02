package com.psiconet.services.interfaces.admin;

import com.psiconet.model.dtos.admin.*;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminService {
    Page<UsersToListAdminDTO> listUsers(String search, RoleEnum role, UserStatusEnum status, Pageable pageable);
    void updateUserStatus(User actor, UUID id, UserStatusUpdateDTO statusDto);
    void updateUser(User actor, UUID id, UserUpdateAdminDTO updateDto);
    UsersToListAdminDTO createAdmin(User actor, AdminCreateDTO dto);
    AdminDashboardDTO dashboard();
    void notifyUser(User actor, AdminNotifyUserDTO dto);
}
