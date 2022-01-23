/** Diagnostic information about a request's status. Should not be used in production code */
public enum RequestStatus {

	RECEIVED,
	PROCESSING,
	WAITING,
	DENIED,
	FULFILLED,
	FINALIZED

}
