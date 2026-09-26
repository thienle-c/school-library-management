package thuvien.server.service;

import thuvien.common.dto.DashboardMetricsDTO;
import thuvien.common.exception.LibraryException;

public interface DashboardService {
    DashboardMetricsDTO getMetrics() throws LibraryException;
    thuvien.common.dto.StudentDashboardDTO getStudentDashboard(Long userId) throws LibraryException, thuvien.common.exception.EntityNotFoundException;
}
