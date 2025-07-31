from fastapi import FastAPI, HTTPException, status, BackgroundTasks
from pydantic import BaseModel, Field
from typing import Dict, List, Literal, Optional
from openai import OpenAI
import os, uuid, json, datetime as dt, re
from fastapi.responses import JSONResponse
from sendgrid import SendGridAPIClient
from sendgrid.helpers.mail import Mail
import os
# ── SQLite (SQLAlchemy 2.x) ─────────────────────────────────────
from sqlalchemy import (
    create_engine, Column, String, DateTime, Text, Integer, select, desc
)
from sqlalchemy.orm import declarative_base, sessionmaker

engine = create_engine("sqlite:///policy.db", future=True)
Session = sessionmaker(bind=engine, expire_on_commit=False)
Base = declarative_base()

# ── DB models ───────────────────────────────────────────────────
class Log(Base):
    __tablename__ = "logs"
    id        = Column(String, primary_key=True)
    timestamp = Column(DateTime, default=dt.datetime.utcnow, index=True)
    policy_id = Column(String, index=True)
    stage     = Column(String)         # "prompt" | "output"
    content   = Column(Text)           # JSON blob

class PolicyRow(Base):
    __tablename__ = "policies"
    id        = Column(String, primary_key=True)
    text      = Column(Text)
    tags      = Column(Text)           # JSON-encoded list
    version   = Column(Integer, default=1)
    updated   = Column(DateTime, default=dt.datetime.utcnow)

Base.metadata.create_all(engine)

# ── In-memory cache (populated at startup) ──────────────────────
POLICIES: Dict[str, Dict] = {}
VERSIONS:  Dict[str, int] = {}

with Session() as s:
    for row in s.query(PolicyRow).all():
        POLICIES[row.id] = {
            "id": row.id,
            "text": row.text,
            "tags": json.loads(row.tags or "[]")
        }
        VERSIONS[row.id] = row.version

# ── Pydantic models ─────────────────────────────────────────────
class PolicyIn(BaseModel):
    id: str
    text: str
    tags: List[str] = Field(default_factory=list)

class CheckPromptIn(BaseModel):
    policy_id: Optional[str] = None
    prompt: str

class Action(BaseModel):
    tool: str
    parameters: dict

class CheckOutputIn(BaseModel):
    policy_id: Optional[str] = None
    llm_output: str
    actions: List[Action]

class DecisionOut(BaseModel):
    alignment: Literal["allow", "deny", "idv", "hil"]
    reason: str                     # <-- single reason string

SEVERITY = {"deny": 3, "idv": 2, "allow": 1, "hil": 0}


# ── Helpers ─────────────────────────────────────────────────────
def write_log(policy_id: str, stage: str, payload: dict):
    with Session.begin() as s:
        s.add(Log(
            id=str(uuid.uuid4()),
            policy_id=policy_id,
            stage=stage,
            content=json.dumps(payload, ensure_ascii=False),
        ))

# 2-step reasoning process for policy compliance
def judge(policies_block: str, stage: str, payload) -> tuple[str, str]:
    """
    Ask the LLM once for ALL combined policies.
    Returns (alignment, reason).
    """
    print(payload)
    client = OpenAI(api_key=os.getenv("AEE_OPENAI_API_KEY"))
    
    # First pass: Initial reasoning and analysis
    system1 = (
        "You are a policy analyst. Carefully analyze content against policies and think through all potential concerns." 
        "Be thorough in your reasoning, think about the context of the request and the policies.\n\n"
        f"POLICIES:\n{policies_block}"
    )
    
    user1 = (
        f"CONTENT TO ANALYZE ({stage}):\n{payload}\n\n"
        "Please think through this step by step:\n"
        "1. What exactly is being requested or proposed? What tool requests are being made?\n"
        "2. Which specific policies are relevant to this request?\n"
        "3. For each relevant policy, does a staged tool execution comply or violate it? Why/why not?\n"
        "4. If an HIL policy is violated, but the user has already submitted information that confirms the policy, mention this and suggest ALLOW and NOT HIL, since we don't want to repeat HIL requests for the same thing.\n"
        "5. You cannot choose 2 policies at the same time, you must choose one.\n"
        "Provide your detailed analysis. Allow policies unless they are truly violated."
    )
    
    initial_response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[
            {"role": "system", "content": system1},
            {"role": "user", "content": user1}
        ],
        temperature=0.1
    ).choices[0].message.content

    print(initial_response)
    
    # SECOND PASS: Self-critique and final structured decision
    system2 = (
        "You are a strict policy compliance engine making final decisions. "
        "Review the analysis and make a conservative final judgment. "
        "If no policy is violated, use ALLOW. You are mainly judging if tool executions should be allowed. If there are no tool executions then always allow or hil if an hil policy is violated. Do not stop the action plan unless one of the tool executions violates a policy, even if the action plan may violate a policy in the future. Do not get ahead of yourself this will cause coordination issues.\n\n"
        "DECISION LEVELS:\n"
        "- ALLOW: Content complies with all policies\n"
        "- DENY: Content clearly violates a specific policy\n"
        "- HIL: Requires human judgment as specified by policy\n"
        "- IDV: Requires additional identity/step-up verification\n\n"
        "If no policy is violated, use ALLOW.\n\n"
        f"POLICIES:\n{policies_block}"
    )
    
    user2 = (
        f"ORIGINAL CONTENT ({stage}):\n{payload}\n\n"
        f"INITIAL ANALYSIS:\n{initial_response}\n\n"
        "Now make your final decision. If the content violates a specific policy, "
        "identify which one. If no policy is violated, use ALLOW. Do not repeat HIL requests for the same thing, if an HIL policy is violated but the user ALREADY submitted information that confirms the policy, use ALLOW and DO NOT USE HIL.\n\n"
        'Respond ONLY as JSON: {"alignment":"ALLOW|DENY|IDV|HIL","policy_id":"<policy_id_if_violation>","reason":"<brief_explanation>"}\n\n'
        'Examples:\n'
        '- {"alignment":"ALLOW","policy_id":null,"reason":"Request complies with all policies"}\n'
        '- {"alignment":"DENY","policy_id":"finance-policy","reason":"Violates spending limit in finance policy"}\n'
        '- {"alignment":"HIL","policy_id":null,"reason":"Ambiguous request requires human review"}\n'
        '- {"alignment":"IDV","policy_id":"auth-policy","reason":"High-risk action requires identity verification"}'
    )
    
    completion = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[
            {"role": "system", "content": system2},
            {"role": "user", "content": user2}
        ],
        response_format={"type": "json_object"},
        temperature=0
    )

    # system = (
    #     "You are a strict policy-compliance engine. "
    #     "Possible answers: ALLOW, DENY, HIL (human in the loop for further review), IDV (step-up verification).\n\n"
    #     f"POLICIES:\n{policies_block}"
    # )
    # user = (
    #     f"INPUT (stage={stage}):\n\"\"\"\n{payload}\n\"\"\"\n\n" +
    #     'Respond ONLY as JSON: {"alignment":"ALLOW|DENY|IDV|HIL","policy_id":"<POLICY_ID>","reason":"<explanation>"}\n\n' +
    #     'Examples:\n' +
    #     '- Allow: {"alignment":"ALLOW","policy_id":null,"reason":"Request complies with all policies"}\n' +
    #     '- Deny: {"alignment":"DENY","policy_id":"POLICY_001","reason":"Policy 001 prohibits purchases exceeding $100000, line 3 of input plan contains purchase for an item worth $100001"}\n' +
    #     '- HIL: {"alignment":"HIL","policy_id":"POLICY_001","reason":"Request requires human in the loop for further review, according to policy 001"}\n' +
    #     '- IDV: {"alignment":"IDV","policy_id":"POLICY_001","reason":"Request requires step-up verification, according to policy 001"}'
    # )
    # client = OpenAI(
    #     api_key=os.getenv("AEE_OPENAI_API_KEY")
    # )
    # completion = client.chat.completions.create(
    #     model="gpt-4o-mini",
    #     messages=[{"role":"system","content":system},
    #               {"role":"user","content":user}],
    #     response_format={"type":"json_object"}
    # )

    raw = completion.choices[0].message.content.strip()
    if raw.startswith("```"):
        raw = re.sub(r"^```[a-zA-Z]*", "", raw).rstrip("`").strip()

    try:
        data = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise HTTPException(502, f"LLM returned invalid JSON: {raw}") from exc

    # to lower unless null
    alignment = data.get("alignment", "").lower() if data.get("alignment", "") else "failed"
    policy_id = data.get("policy_id", "").lower() if data.get("policy_id", "") else None
    reason    = data.get("reason", "No reason provided") if data.get("reason", "") else "No reason provided"

    if alignment not in {"allow", "deny", "idv", "hil"}:
        raise HTTPException(502, f"LLM returned unknown alignment value: {alignment}")

    return alignment, policy_id, reason

# ── FastAPI app ─────────────────────────────────────────────────
app = FastAPI(title="Policy API", version="1.4")

sg = SendGridAPIClient(os.environ["SENDGRID_API_KEY"])

def _email_task(to, subject, html):
    sg.send(Mail(from_email="you@gmail.com",
                 to_emails=to, subject=subject, html_content=html))

@app.post("/invite/{email}")
def invite(email: str, tasks: BackgroundTasks):
    tasks.add_task(_email_task, email, "Invite", "<p>Click link…</p>")
    return {"status": "sent"}

# ── Policy CRUD ────────────────────────────────────────────────
@app.post("/policies")
def create_policy(p: PolicyIn):
    # increment version
    new_version = VERSIONS.get(p.id, 0) + 1
    VERSIONS[p.id] = new_version
    POLICIES[p.id] = p.dict()

    tags_json = json.dumps(p.tags, ensure_ascii=False)

    with Session.begin() as s:
        row = s.get(PolicyRow, p.id)
        if row:
            row.text    = p.text
            row.tags    = tags_json
            row.version = new_version
            row.updated = dt.datetime.utcnow()
        else:
            s.add(PolicyRow(
                id=p.id, text=p.text, tags=tags_json,
                version=new_version
            ))

    return {"message": "stored", "version": new_version}

@app.delete("/policies/{policy_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_policy(policy_id: str):
    if policy_id not in POLICIES:
        raise HTTPException(404, "Unknown policy_id")

    POLICIES.pop(policy_id)
    VERSIONS.pop(policy_id, None)

    with Session.begin() as s:
        row = s.get(PolicyRow, policy_id)
        if row:
            s.delete(row)

    return  # 204 No Content

from fastapi.responses import JSONResponse

@app.get("/policies", response_class=JSONResponse)
def list_policies():
    """
    Returns an array like:
    [
      {"id": "finance-v1", "version": 3, "tags": ["finance"], "text": "..."},
      ...
    ]
    """
    return [
        {
            "id": p["id"],
            "version": VERSIONS.get(p["id"], 1),
            "tags": p["tags"],
            "text": p["text"],
        }
        for p in POLICIES.values()
    ]

@app.get("/logs", response_class=JSONResponse)
def list_logs(stage: str, limit: int = 20):
    """
    Query params:
      • stage = prompt | output
      • limit = n rows (default 20)
    """
    with Session() as s:
        rows = (
            s.execute(
                select(Log).where(Log.stage == stage)
                .order_by(desc(Log.timestamp))
                .limit(limit)
            )
            .scalars()
            .all()
        )
        return [
            {
                "timestamp": row.timestamp.isoformat(),
                "policy_id": row.policy_id,
                "content": json.loads(row.content),
            }
            for row in rows
        ]

# ── Check endpoints ────────────────────────────────────────────
def _get_active(req_id: Optional[str]):
    if req_id:
        pol = POLICIES.get(req_id)
        return [pol] if pol else []
    return list(POLICIES.values())

@app.post("/check/prompt", response_model=DecisionOut)
def check_prompt(req: CheckPromptIn):
    active = _get_active(req.policy_id)
    if not active:
        raise HTTPException(404, "No policies loaded" if req.policy_id is None
                                   else "Unknown policy_id")

    # --- ONE LLM CALL --------------------------------------------------------
    policies_block = "\n\n".join(
        f"--- POLICY #{i+1} ---\n{p['text']}" for i, p in enumerate(active)
    )
    alignment, policy_id, reason = judge(policies_block, "prompt", req.prompt)

    # log once per policy (audit trail)
    for p in active:
        write_log(p["id"], "prompt", {"prompt": req.prompt})

    return {"alignment": alignment, "policy_id": policy_id, "reason": reason}


@app.post("/check/output", response_model=DecisionOut)
def check_output(req: CheckOutputIn):
    print(req)
    active = _get_active(req.policy_id)
    if not active:
        raise HTTPException(404, "No policies loaded" if req.policy_id is None
                                   else "Unknown policy_id")

    bundle = {"llm_output": req.llm_output,
              "actions": [a.dict() for a in req.actions]}

    policies_block = "\n\n".join(
        f"--- POLICY ---\n{p['text']}" for p in active
    )
    alignment, policy_id, reason = judge(policies_block, "output", bundle)

    for p in active:
        write_log(p["id"], "output", bundle)

    return {"alignment": alignment, "policy_id":policy_id, "reason": reason}

# ——— Run:   uvicorn policy_api:app --reload ———