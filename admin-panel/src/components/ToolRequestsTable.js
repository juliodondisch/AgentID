import ModernTable from "./ModernTable"
import StatusBadge from "./StatusBadge"
import TimeInfo from "./TimeInfo"
import { formatDateTime } from "../lib/utils"

const ToolRequestsTable = ({ toolRequests }) => {
  const columns = [
    {
      key: "tool_request_id",
      label: "Request ID",
      render: (value) => <span className="font-mono text-sm font-medium text-blue-600">{value}</span>,
    },
    {
      key: "action_id",
      label: "Action ID",
      render: (value) => <span className="text-sm text-gray-900">{value}</span>,
    },
    {
      key: "policy_id",
      label: "Policy ID",
      render: (value) => <span className="text-sm text-gray-900">{value}</span>,
    },
    {
      key: "tool_status",
      label: "Tool Status",
      render: (value) => <StatusBadge status={value} />,
    },
    {
      key: "exec_start_time",
      label: "Exec Start Time",
      render: (value) => <span className="text-sm text-gray-900">{formatDateTime(value)}</span>,
    },
    {
      key: "exec_end_time",
      label: "Exec End Time",
      render: (value) => <span className="text-sm text-gray-900">{formatDateTime(value)}</span>,
    },
    {
      key: "tool_name",
      label: "Tool Name",
      render: (value) => <span className="text-sm text-gray-900">{value}</span>,
    },
  ]

  const renderExpandedRow = (toolRequest) => (
    <div className="space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <h4 className="text-sm font-medium text-gray-700 mb-2">Request Parameters</h4>
          <div className="bg-gray-50 rounded-lg p-3">
            <div className="text-xs text-gray-600 font-mono whitespace-pre-wrap break-words break-all">
              {JSON.stringify(toolRequest.request_parameters, null, 2)}
            </div>
          </div>
        </div>
        <div>
          <h4 className="text-sm font-medium text-gray-700 mb-2">Response Data</h4>
          <div className="bg-gray-50 rounded-lg p-3">
            <div className="text-xs text-gray-600 font-mono whitespace-pre-wrap break-words break-all">
              {JSON.stringify(toolRequest.response_data, null, 2)}
            </div>
          </div>
        </div>
      </div>
      <div>
        <h4 className="text-sm font-medium text-gray-700 mb-2">Execution Timeline</h4>
        <TimeInfo startTime={toolRequest.start_time} endTime={toolRequest.end_time} />
      </div>
    </div>
  )

  return (
    <ModernTable
      data={toolRequests}
      columns={columns}
      searchable={true}
      sortable={true}
      expandable={true}
      renderExpandedRow={renderExpandedRow}
    />
  )
}

export default ToolRequestsTable
