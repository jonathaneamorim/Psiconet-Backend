package com.psiconet.services.implement.admin;

import com.psiconet.infra.exceptions.EntityNotFoundException;
import com.psiconet.mapper.UserMapper;
import com.psiconet.model.dtos.admin.UserStatusUpdateDTO;
import com.psiconet.model.dtos.admin.UserUpdateAdminDTO;
import com.psiconet.model.dtos.admin.UsersToListAdminDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.repositories.access.UserRepository;
import com.psiconet.services.interfaces.admin.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<UsersToListAdminDTO> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(userMapper::toAdminListDto);
    }

    @Override
    @Transactional
    public void updateUserStatus(UUID id, UserStatusUpdateDTO statusDto) {
        User user = findUserById(id);
        user.setStatus(statusDto.getStatus());
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void updateUser(UUID id, UserUpdateAdminDTO updateDto) {
        User user = findUserById(id);
        user.setFullName(updateDto.getFullName());
        user.setPhone(updateDto.getPhone());
        user.setStatus(updateDto.getStatus());
        userRepository.save(user);
    }

    private User findUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(User.class, id));
    }
}
