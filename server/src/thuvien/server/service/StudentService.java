package thuvien.server.service;

import java.util.List;
import thuvien.common.dto.StudentDTO;
import thuvien.common.exception.EntityNotFoundException;
import thuvien.common.exception.LibraryException;

public interface StudentService {
    StudentDTO getStudentById(Long id) throws EntityNotFoundException, LibraryException;
    StudentDTO getStudentByUserId(Long userId) throws EntityNotFoundException, LibraryException;
    StudentDTO getStudentByCode(String code) throws EntityNotFoundException, LibraryException;
    List<StudentDTO> getAllStudents() throws LibraryException;
    Long createStudent(StudentDTO student) throws LibraryException;
    boolean updateStudent(StudentDTO student) throws LibraryException;
    boolean deleteStudent(Long id) throws EntityNotFoundException, LibraryException;
    boolean hardDeleteStudent(Long id) throws EntityNotFoundException, thuvien.common.exception.ValidationException, LibraryException;
    String generateActivationCode(Long studentId) throws EntityNotFoundException, LibraryException;
    boolean registerStudentAccount(thuvien.common.dto.RegisterStudentRequestDTO request) throws thuvien.common.exception.ValidationException, LibraryException;
}

