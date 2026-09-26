package thuvien.server.service;

import java.util.List;
import thuvien.common.dto.UserDTO;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;

public interface UserService {
    UserDTO getUserById(Long id) throws EntityNotFoundException, LibraryException;
    UserDTO getUserByUsername(String username) throws EntityNotFoundException, LibraryException;
    List<UserDTO> getAllUsers() throws LibraryException;
    Long createUser(UserDTO user, String plainPassword) throws LibraryException;
    boolean updateUser(UserDTO user) throws LibraryException;
    boolean deleteUser(Long id) throws LibraryException;
    boolean changePassword(Long userId, String currentPassword, String newPassword)
            throws thuvien.common.exception.ValidationException, EntityNotFoundException, LibraryException;
}
