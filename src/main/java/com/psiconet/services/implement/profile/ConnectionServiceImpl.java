package com.psiconet.services.implement.profile;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.exceptions.EntityNotFoundException;
import com.psiconet.mapper.ConnectionMapper;
import com.psiconet.model.dtos.profile.ActiveConnectionDTO;
import com.psiconet.model.dtos.profile.ConnectionDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.profile.Psychologist;
import com.psiconet.model.entities.profile.conection.Connection;
import com.psiconet.model.enums.conection.ConectionStatusEnum;
import com.psiconet.repositories.access.UserRepository;
import com.psiconet.repositories.profile.ConnectionRepository;
import com.psiconet.repositories.profile.PsychologistRepository;
import com.psiconet.services.interfaces.profile.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConnectionServiceImpl implements ConnectionService {

    private final ConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final PsychologistRepository psychologistRepository;
    private final ConnectionMapper connectionMapper;

    @Override
    @Transactional
    public void sendRequest(User sender, UUID targetUserId) {
        if (sender.getId().equals(targetUserId)) {
            throw new BusinessException("connection", "Você não pode conectar-se a si mesmo.");
        }

        if (sender.getRole() == com.psiconet.model.enums.RoleEnum.ADMIN) {
            throw new BusinessException("connection", "Administradores não podem participar de conexões profissionais.");
        }

        User receiver = userRepository.findById(targetUserId)
                .orElseThrow(() -> new EntityNotFoundException(User.class, targetUserId));

        if (receiver.getRole() == com.psiconet.model.enums.RoleEnum.ADMIN) {
            throw new BusinessException("connection", "Não é possível conectar-se a um administrador.");
        }

        Optional<Connection> existing = connectionRepository.findActiveOrPendingBetween(sender, receiver);

        if (existing.isPresent()) {
            Connection conn = existing.get();
            
            if (conn.getStatus() == ConectionStatusEnum.PENDING) {
                return;
            }

            if (conn.getStatus() == ConectionStatusEnum.ACCEPTED) {
                throw new BusinessException("connection", "Já existe uma conexão ativa.");
            }

            if (conn.getStatus() == ConectionStatusEnum.REJECTED) {
                boolean wasSenderWhoRejected = conn.getReceiver().getId().equals(sender.getId());
                
                if (!wasSenderWhoRejected) {
                    throw new BusinessException("connection", "Sua solicitação anterior foi rejeitada. Apenas o outro usuário pode iniciar uma nova conexão.");
                }
                
                conn.setStatus(ConectionStatusEnum.REMOVED);
                connectionRepository.save(conn);
            } else {
                throw new BusinessException("connection", "Já existe uma solicitação de conexão em processamento.");
            }
        }

        Connection connection = Connection.builder()
                .sender(sender)
                .receiver(receiver)
                .status(ConectionStatusEnum.PENDING)
                .build();

        connectionRepository.save(connection);
    }

    @Override
    @Transactional
    public void acceptRequest(User user, UUID connectionId) {
        Connection connection = findAndValidateOwnership(user, connectionId, true);

        if (connection.getStatus() != ConectionStatusEnum.PENDING) {
            throw new BusinessException("connection", "Apenas solicitações pendentes podem ser aceitas.");
        }

        connection.setStatus(ConectionStatusEnum.ACCEPTED);
        connection.setAcceptedAt(LocalDateTime.now());
        connectionRepository.save(connection);
    }

    @Override
    @Transactional
    public void rejectRequest(User user, UUID connectionId) {
        Connection connection = findAndValidateOwnership(user, connectionId, true);

        if (connection.getStatus() != ConectionStatusEnum.PENDING) {
            throw new BusinessException("connection", "Apenas solicitações pendentes podem ser rejeitadas.");
        }

        connection.setStatus(ConectionStatusEnum.REJECTED);
        connectionRepository.save(connection);
    }

    @Override
    @Transactional
    public void removeConnection(User user, UUID connectionId) {
        Connection connection = findAndValidateOwnership(user, connectionId, false);

        if (connection.getStatus() == ConectionStatusEnum.REMOVED) {
            return;
        }

        connection.setStatus(ConectionStatusEnum.REMOVED);
        connectionRepository.save(connection);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ConnectionDTO> listPendingRequests(User user, Pageable pageable) {
        return connectionRepository.findByReceiverAndStatus(user, ConectionStatusEnum.PENDING, pageable)
                .map(connectionMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ActiveConnectionDTO> listAcceptedConnections(User user, Pageable pageable) {
        // Traduz o campo de ordenação do DTO (connectedAt) para o campo da entidade (acceptedAt)
        Sort sort = pageable.getSort();
        if (sort.isSorted()) {
            List<Sort.Order> orders = sort.stream()
                    .map(order -> order.getProperty().equals("connectedAt") 
                            ? new Sort.Order(order.getDirection(), "acceptedAt") 
                            : order)
                    .collect(Collectors.toList());
            pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
        }

        Page<Connection> connections = connectionRepository.findByUserAndStatus(user, ConectionStatusEnum.ACCEPTED, pageable);
        
        if (connections.isEmpty()) {
            return Page.empty(pageable);
        }

        List<User> otherUsers = connections.getContent().stream()
                .map(c -> c.getSender().getId().equals(user.getId()) ? c.getReceiver() : c.getSender())
                .collect(Collectors.toList());

        Map<UUID, Psychologist> psychologistMap = psychologistRepository
                .findByUserIn(otherUsers)
                .stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));

        return connections.map(connection -> {
            User otherUser = connection.getSender().getId().equals(user.getId()) 
                    ? connection.getReceiver() 
                    : connection.getSender();
            
            Psychologist psychologist = psychologistMap.get(otherUser.getId());
            
            return ActiveConnectionDTO.builder()
                    .connectionId(connection.getId())
                    .connectedAt(connection.getAcceptedAt())
                    .user(connectionMapper.toConnectedUserDto(otherUser, psychologist))
                    .build();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public com.psiconet.model.dtos.profile.ConnectionStatusInfoDTO getConnectionStatus(User currentUser, User targetUser) {
        if (currentUser == null || targetUser == null) {
            return com.psiconet.model.dtos.profile.ConnectionStatusInfoDTO.builder()
                    .status(com.psiconet.model.enums.conection.UserConnectionStatusEnum.NONE)
                    .build();
        }

        return connectionRepository.findActiveOrPendingBetween(currentUser, targetUser)
                .map(c -> {
                    com.psiconet.model.enums.conection.UserConnectionStatusEnum status;
                    if (c.getStatus() == ConectionStatusEnum.ACCEPTED) {
                        status = com.psiconet.model.enums.conection.UserConnectionStatusEnum.CONNECTED;
                    } else if (c.getStatus() == ConectionStatusEnum.PENDING) {
                        if (c.getSender().getId().equals(currentUser.getId())) {
                            status = com.psiconet.model.enums.conection.UserConnectionStatusEnum.PENDING_SENT;
                        } else {
                            status = com.psiconet.model.enums.conection.UserConnectionStatusEnum.PENDING_RECEIVED;
                        }
                    } else {
                        status = com.psiconet.model.enums.conection.UserConnectionStatusEnum.NONE;
                    }

                    return com.psiconet.model.dtos.profile.ConnectionStatusInfoDTO.builder()
                            .connectionId(c.getId())
                            .status(status)
                            .build();
                })
                .orElse(com.psiconet.model.dtos.profile.ConnectionStatusInfoDTO.builder()
                        .status(com.psiconet.model.enums.conection.UserConnectionStatusEnum.NONE)
                        .build());
    }

    private Connection findAndValidateOwnership(User user, UUID connectionId, boolean mustBeReceiver) {
        Connection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new EntityNotFoundException(Connection.class, connectionId));

        if (mustBeReceiver) {
            if (!connection.getReceiver().getId().equals(user.getId())) {
                throw new BusinessException("connection", "Você não tem permissão para realizar esta ação.");
            }
        } else {
            if (!connection.getSender().getId().equals(user.getId()) && !connection.getReceiver().getId().equals(user.getId())) {
                throw new BusinessException("connection", "Você não tem permissão para realizar esta ação.");
            }
        }

        return connection;
    }
}
