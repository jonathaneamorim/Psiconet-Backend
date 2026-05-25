package com.psiconet.services.interfaces.profile;

import com.psiconet.model.dtos.profile.ActiveConnectionDTO;
import com.psiconet.model.dtos.profile.ConnectionDTO;
import com.psiconet.model.entities.access.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ConnectionService {
    void sendRequest(User sender, UUID targetUserId);
    void acceptRequest(User user, UUID connectionId);
    void rejectRequest(User user, UUID connectionId);
    void removeConnection(User user, UUID connectionId);
    Page<ConnectionDTO> listPendingRequests(User user, Pageable pageable);
    Page<ActiveConnectionDTO> listAcceptedConnections(User user, Pageable pageable);

    com.psiconet.model.dtos.profile.ConnectionStatusInfoDTO getConnectionStatus(User currentUser, User targetUser);
}
