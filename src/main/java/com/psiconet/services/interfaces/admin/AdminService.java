package com.psiconet.services.interfaces.admin;

import com.psiconet.model.dtos.admin.UserStatusUpdateDTO;
import com.psiconet.model.dtos.admin.UserUpdateAdminDTO;
import com.psiconet.model.dtos.admin.UsersToListAdminDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminService {
    Page<UsersToListAdminDTO> listUsers(Pageable pageable);
    void updateUserStatus(UUID id, UserStatusUpdateDTO statusDto);
    void updateUser(UUID id, UserUpdateAdminDTO updateDto);
}
