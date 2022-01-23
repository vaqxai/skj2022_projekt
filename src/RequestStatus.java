/** Diagnostic information about a request's status. Should not be used in production code */
public enum RequestStatus {

	/** Request was just received */
	RECEIVED,

	/** Request is being processed/worked on */
	PROCESSING,

	/** Request is waiting for searches to be completed */
	WAITING,

	/** Request has been denied due to insufficient resources */
	DENIED,

	/** Request has been fully locked and will shortly be allocated */
	FULFILLED,

	/** Request has been allocated and a response to the client has been sent */
	FINALIZED

}
