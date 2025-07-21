import { useState, useEffect } from "react"
import { ChevronLeft, ChevronRight } from "lucide-react"
import { mockData, mockSessions, mockActionPlans } from "./mockData"
import StatusBadge from "./components/StatusBadge"
import Card from "./components/Card"
import TimeInfo from "./components/TimeInfo"
import ToolRequestsTable from "./components/ToolRequestsTable"
import SessionsList from "./components/SessionsList"
import SessionDetails from "./components/SessionDetails"
import Header from "./components/Header"
import { formatDateTime } from "./lib/utils"

function App() {
  const [currentView, setCurrentView] = useState("sessions")
  const [selectedSession, setSelectedSession] = useState(null)
  const [selectedActionPlan, setSelectedActionPlan] = useState(null)

  const [allSessions, setAllSessions] = useState(mockSessions)
  const [selectedSessionDetails, setSelectedSessionDetails] = useState(null)
  const [selectedActionPlanDetails, setSelectedActionPlanDetails] = useState(null)
  const [sessionActionPlans, setSessionActionPlans] = useState(mockActionPlans)
  const [action, setAction] = useState(mockData.action);
  const [policyCheck, setPolicyCheck] = useState(mockData.policyCheck);
  const [feedback, setFeedback] = useState(mockData.feedback);
  const [toolRequests, setToolRequests] = useState(mockData.toolRequests);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);

  // Load sessions from database on page load
  useEffect(() => {
    const loadInitialSessions = async () => {
      try {
        const allSessionreq = await fetch(`http://localhost:5920/querysession/all`).then(res => res.json());
        setAllSessions(Object.values(allSessionreq));
      } catch (err) {
        console.log("Failed to load initial sessions:", err);
        setAllSessions(mockSessions);
      } finally {
        setInitialLoading(false);
      }
    };
    
    loadInitialSessions();
  }, []);

  const calculateDuration = (startTime, endTime) => {
    const start = new Date(startTime)
    const end = new Date(endTime)
    const diffMs = end - start
    const diffMins = Math.round((diffMs / 1000 / 60) * 10) / 10
    return `${diffMins} minutes`
  }

  const calculateDurationSeconds = (startTime, endTime) => {
    const start = new Date(startTime)
    const end = new Date(endTime)
    const diffMs = end - start
    const diffSecs = Math.round(diffMs / 1000)
    return `${diffSecs} seconds`
  }

  const handleSessionClick = async (session) => {
    setSelectedSession(session);
    setCurrentView("session-details");
    setDetailsLoading(true);
    try {
      const [sessionreq, actionplansreq] = await Promise.all([
        fetch(`http://localhost:5920/querysession/${session.session_id}`).then(res => res.json()),
        fetch(`http://localhost:5920/actionplan/session/${session.session_id}`).then(res => res.json()),
      ]);
      setSessionActionPlans(Object.values(actionplansreq));
      setSelectedSessionDetails(sessionreq);
    } catch (err) {
      console.log(err);
      setSessionActionPlans(mockActionPlans);
      setSelectedSessionDetails(mockSessions[0] || null);
    } finally {
      setDetailsLoading(false);
    }
  }

  const handleActionPlanClick = async (actionPlan, session) => {
    setSelectedActionPlan(actionPlan);
    setCurrentView("action-details");
    setDetailsLoading(true);
    try {
      const [actionreq, policyCheckreq, feedbackreq, toolRequestsreq] = await Promise.all([
        fetch(`http://localhost:5920/actionplan/${actionPlan.action_id}`).then(res => res.json()),
        fetch(`http://localhost:5920/policycheck/actionplan/${actionPlan.action_id}`).then(res => res.json()),
        fetch(`http://localhost:5920/feedback/actionplan/${actionPlan.action_id}`).then(res => res.json()),
        fetch(`http://localhost:5920/toolrequest/actionplan/${actionPlan.action_id}`).then(res => res.json()),
      ]);

      console.log(actionreq);
      console.log(policyCheckreq);
      console.log(feedbackreq);
      console.log(toolRequestsreq);

      setSelectedActionPlanDetails(actionreq);

      // check if not null
      if (feedbackreq.length !== 0) {
        // Temp getting rid of the Feedback field in feedbackreq and replacing it with null bc we currently don't have natural language feedback
        feedbackreq.feedback = "N/A";
      }

      setAction(Object.values(actionreq));
      setPolicyCheck(Object.values(policyCheckreq)[0]);
      setFeedback(Object.values(feedbackreq)[0]);
      setToolRequests(Object.values(toolRequestsreq));
    } catch (err) {
      setSelectedActionPlanDetails(mockData.action || null);
      setAction(mockData.action || null);
      setPolicyCheck(mockData.policyCheck || null);
      setFeedback(mockData.feedback || null);
      setToolRequests(mockData.toolRequests || []);
    } finally {
      setDetailsLoading(false);
    }
  };

  const handleBackToSessions = async () => {
    setCurrentView("sessions");
    setDetailsLoading(true);
    try {
      const allSessionreq = await fetch(`http://localhost:5920/querysession/all`).then(res => res.json());
      console.log(allSessionreq);
      setAllSessions(Object.values(allSessionreq));
    } catch (err) {
      setAllSessions(mockSessions);
    } finally {
      setSelectedSession(null);
      setSelectedActionPlan(null);
      setSelectedSessionDetails(null);
      setSelectedActionPlanDetails(null);
      setSessionActionPlans([]);
      setAction(null);
      setPolicyCheck(null);
      setFeedback(null);
      setToolRequests([]);
      setDetailsLoading(false);
    }
  }

  const handleBackToSessionDetails = async () => {
    setCurrentView("session-details");
    setSelectedActionPlan(null);
    setDetailsLoading(true);
    try {
      const [sessionreq, actionplansreq] = await Promise.all([
        fetch(`http://localhost:5920/querysession/${session.session_id}`).then(res => res.json()),
        fetch(`http://localhost:5920/actionplan/session/${session.session_id}`).then(res => res.json()),
      ]);
      setSelectedSessionDetails(sessionreq);
      setSessionActionPlans(Object.values(actionplansreq));
      setSelectedActionPlanDetails(null);
    } catch (err) {
      setSelectedActionPlanDetails(null);
    } finally {
      setSelectedActionPlanDetails(null);
      setAction(null);
      setPolicyCheck(null);
      setFeedback(null);
      setToolRequests([]);
      setDetailsLoading(false);
    }
  }

  if (currentView === "sessions") {
    return (
      <div className="min-h-screen bg-gray-100">
        <Header currentView={currentView} />

        <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <div className="mb-6 w-full">
            <h1 className="text-2xl font-bold text-gray-900">Alignment Engine Sessions</h1>
            <p className="mt-2 text-gray-600">Monitor and analyze the Alignment Engine system sessions and their performance.</p>
          </div>
          {initialLoading ? (
            <div className="flex justify-center items-center py-20">
              <span className="text-blue-600 text-lg font-semibold">Loading sessions...</span>
            </div>
          ) : (
            <SessionsList sessions={allSessions} onSessionClick={handleSessionClick} />
          )}
        </main>
      </div>
    )
  }

  if (currentView === "session-details") {
    return (
      <div className="min-h-screen bg-gray-100">
        <Header currentView={currentView} />

        <div className="bg-blue-50 border-b border-blue-200">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex items-center py-3">
              <span onClick={handleBackToSessions} style={{cursor:'pointer',display:'inline-flex',alignItems:'center'}}>
                <ChevronLeft className="h-4 w-4 text-blue-600" />
              </span>
              <nav className="flex ml-2" aria-label="Breadcrumb">
                <ul className="flex items-center space-x-2 text-sm list-none" style={{listStyle:'none'}}>
                  <li>
                    <a
                      onClick={handleBackToSessions}
                      className="text-blue-600 hover:text-blue-800 transition-colors cursor-pointer user-select-none"
                    >
                      Sessions
                    </a>
                  </li>
                  <li className="flex items-center">
                    <ChevronRight className="h-3 w-3 text-blue-400 mx-1" />
                    <span className="text-blue-900 font-medium">Session {selectedSessionDetails?.session_id}</span>
                  </li>
                </ul>
              </nav>
            </div>
          </div>
        </div>

        <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <SessionDetails
            session={selectedSessionDetails}
            actionPlans={sessionActionPlans}
            onActionPlanClick={(actionPlan) => handleActionPlanClick(actionPlan, selectedSessionDetails)}
          />
        </main>
      </div>
    )
  }

  if (currentView === "action-details") {
    return (
      <div className="min-h-screen bg-gray-100">
        <Header currentView={currentView} />

        <div className="bg-blue-50 border-b border-blue-200">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex items-center py-3">
              <span onClick={handleBackToSessionDetails} style={{cursor:'pointer',display:'inline-flex',alignItems:'center'}}>
                <ChevronLeft className="h-4 w-4 text-blue-600" />
              </span>
              <nav className="flex ml-2" aria-label="Breadcrumb">
                <ul className="flex items-center space-x-2 text-sm list-none" style={{listStyle:'none'}}>
                  <li>
                    <a
                      onClick={handleBackToSessions}
                      className="text-blue-600 hover:text-blue-800 transition-colors cursor-pointer user-select-none"
                    >
                      Sessions
                    </a>
                  </li>
                  <li className="flex items-center">
                    <ChevronRight className="h-3 w-3 text-blue-400 mx-1" />
                    <a
                      onClick={handleBackToSessionDetails}
                      className="text-blue-600 hover:text-blue-800 transition-colors cursor-pointer user-select-none"
                    >
                      Session {selectedSessionDetails?.session_id}
                    </a>
                  </li>
                  <li className="flex items-center">
                    <ChevronRight className="h-3 w-3 text-blue-400 mx-1" />
                    <span className="text-blue-900 font-medium">
                      Action Plan {
                        selectedActionPlanDetails?.action_id
                          || action?.action_id
                          || "N/A"
                      }
                    </span>
                  </li>
                </ul>
              </nav>
            </div>
          </div>
        </div>

        <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <Card title="Action Plan Details">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="col-span-2">
                <h3 className="text-sm font-medium text-gray-500">Plan</h3>
                <p className="mt-1 text-sm text-gray-900">{selectedActionPlanDetails?.plan || action?.plan || "N/A"}</p>
              </div>
              <div>
                <h3 className="text-sm font-medium text-gray-500">Plan Status</h3>
                <div className="mt-1">
                  <StatusBadge status={selectedActionPlanDetails?.plan_status || action?.plan_status || "N/A"} />
                </div>
              </div>
              <div>
                <h3 className="text-sm font-medium text-gray-500">Loop Count</h3>
                <p className="mt-1 text-sm text-gray-900">{selectedActionPlanDetails?.loop_count || action?.loop_count || "N/A"}</p>
              </div>
              <div>
                <h3 className="text-sm font-medium text-gray-500">Duration</h3>
                <p className="mt-1 text-sm text-gray-900">
                  {(selectedActionPlanDetails?.start_time || action?.start_time)
                    ? calculateDuration(
                        selectedActionPlanDetails?.start_time || action?.start_time,
                        selectedActionPlanDetails?.end_time || action?.end_time
                      )
                    : "N/A"}
                </p>
              </div>
              <div className="col-span-1 md:col-span-2">
                <TimeInfo
                  startTime={selectedActionPlanDetails?.start_time || action?.start_time || undefined}
                  endTime={selectedActionPlanDetails?.end_time || action?.end_time || undefined}
                />
              </div>
            </div>
          </Card>

          <Card title="Policy Check" className="mt-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div>
                <h3 className="text-sm font-medium text-gray-500">Policy Check ID</h3>
                <p className="mt-1 text-sm text-gray-900">{policyCheck?.policy_check_id || "N/A"}</p>
              </div>
              <div>
                <h3 className="text-sm font-medium text-gray-500">Action ID</h3>
                <p className="mt-1 text-sm text-gray-900">{policyCheck?.action_id || "N/A"}</p>
              </div>
              <div>
                <h3 className="text-sm font-medium text-gray-500">Decision</h3>
                <div className="mt-1">
                  <StatusBadge status={policyCheck?.decision || "N/A"} />
                </div>
              </div>
              <div>
                <h3 className="text-sm font-medium text-gray-500">Policy Triggered</h3>
                <p className="mt-1 text-sm text-gray-900">{policyCheck?.policy_triggered || "N/A"}</p>
              </div>
              <div>
                <h3 className="text-sm font-medium text-gray-500">Policy Status</h3>
                <div className="mt-1">
                  <StatusBadge status={policyCheck?.policy_status || "N/A"} />
                </div>
              </div>
              <div>
                <h3 className="text-sm font-medium text-gray-500">Start Time</h3>
                <p className="mt-1 text-sm text-gray-900">{formatDateTime(policyCheck?.start_time) || "N/A"}</p>
              </div>
              <div>
                <h3 className="text-sm font-medium text-gray-500">End Time</h3>
                <p className="mt-1 text-sm text-gray-900">{formatDateTime(policyCheck?.end_time) || "N/A"}</p>
              </div>
            </div>
          </Card>

          <Card title="Feedback" className="mt-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div>
                <h3 className="text-sm font-medium text-gray-500">Action ID</h3>
                <p className="mt-1 text-sm text-gray-900">{feedback?.action_id || "N/A"}</p>
              </div>
              <div className="col-span-1 md:col-span-2">
                <h3 className="text-sm font-medium text-gray-500">Feedback</h3>
                <p className="mt-1 text-sm text-gray-900">{feedback?.feedback || "N/A"}</p>
              </div>
              <div>
                <h3 className="text-sm font-medium text-gray-500">Decision</h3>
                <div className="mt-1">
                  <StatusBadge status={feedback?.decision || "N/A"} />
                </div>
              </div>
              <div>
                <h3 className="text-sm font-medium text-gray-500">Start Time</h3>
                <p className="mt-1 text-sm text-gray-900">{formatDateTime(feedback?.start_time) || "N/A"}</p>
              </div>
              <div>
                <h3 className="text-sm font-medium text-gray-500">End Time</h3>
                <p className="mt-1 text-sm text-gray-900">{formatDateTime(feedback?.end_time) || "N/A"}</p>
              </div>
            </div>
          </Card>

          <div className="mt-6">
            <h2 className="text-lg font-medium text-gray-900 mb-4">Tool Requests</h2>
            <ToolRequestsTable toolRequests={toolRequests} />
          </div>
        </main>
      </div>
    )
  }
}

export default App
