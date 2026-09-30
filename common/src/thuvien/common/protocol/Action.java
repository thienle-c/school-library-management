package thuvien.common.protocol;

/**
 * Protocol actions supported by the Remote Library Server.
 */
public enum Action {
    // Authentication
    LOGIN,
    LOGOUT,
    GET_CURRENT_USER,
    CHANGE_PASSWORD,
    REGISTER_STUDENT,

    // User & Role Management
    LIST_USERS,
    GET_USER,
    CREATE_USER,
    UPDATE_USER,
    DELETE_USER,

    // Student Management
    LIST_STUDENTS,
    GET_STUDENT,
    CREATE_STUDENT,
    UPDATE_STUDENT,
    DELETE_STUDENT,
    GENERATE_STUDENT_ACTIVATION,

    // Category Management
    LIST_CATEGORIES,
    GET_CATEGORY,
    CREATE_CATEGORY,
    UPDATE_CATEGORY,
    DELETE_CATEGORY,

    // Author Management
    LIST_AUTHORS,
    GET_AUTHOR,
    CREATE_AUTHOR,
    UPDATE_AUTHOR,
    DELETE_AUTHOR,

    // Book Management & Catalog Search
    LIST_BOOKS,
    GET_BOOK,
    CREATE_BOOK,
    UPDATE_BOOK,
    DELETE_BOOK,
    SEARCH_BOOKS,

    // Borrow & Return Transactions
    BORROW_BOOK,
    RETURN_BOOK,
    LIST_BORROW_RECORDS,
    GET_STUDENT_ACTIVE_BORROWS,

    // Fine Management
    LIST_FINES,
    CALCULATE_FINE,
    PAY_FINE,

    // Reservations
    CREATE_RESERVATION,
    CANCEL_RESERVATION,
    LIST_RESERVATIONS,

    // Dashboard & Reports
    GET_DASHBOARD_METRICS,
    GET_STUDENT_DASHBOARD,

    // Audit Log
    LIST_AUDIT_LOGS,

    // Search Autocomplete Suggestions
    SEARCH_SUGGESTIONS,

    // Health Check
    PING;

    /**
     * Determines whether this action is idempotent and safe for automatic retry.
     */
    public boolean isReadOnly() {
        switch (this) {
            case PING:
            case GET_CURRENT_USER:
            case LIST_USERS:
            case GET_USER:
            case LIST_STUDENTS:
            case GET_STUDENT:
            case LIST_CATEGORIES:
            case GET_CATEGORY:
            case LIST_AUTHORS:
            case GET_AUTHOR:
            case LIST_BOOKS:
            case GET_BOOK:
            case SEARCH_BOOKS:
            case LIST_BORROW_RECORDS:
            case GET_STUDENT_ACTIVE_BORROWS:
            case LIST_FINES:
            case CALCULATE_FINE:
            case LIST_RESERVATIONS:
            case GET_DASHBOARD_METRICS:
            case GET_STUDENT_DASHBOARD:
            case LIST_AUDIT_LOGS:
            case SEARCH_SUGGESTIONS:
                return true;
            default:
                return false;
        }
    }
}
