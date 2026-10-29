"""Build the evidence-based handover document using only stdlib and matplotlib.

Run from the repository root with system Python. No application dependencies change.
The DOCX is native Office Open XML with embedded diagrams and editable tables.
"""
from pathlib import Path
import json
import re
import subprocess
import zipfile
from xml.sax.saxutils import escape
from xml.etree import ElementTree as ET
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
ASSETS = OUT / 'assets'
ASSETS.mkdir(exist_ok=True)
TITLE = 'YamanTracker — System Analysis and Technical Architecture'
W = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'
body, rels, images = [], [], []
pages = []

def text_run(s, bold=False):
    return '<w:r>' + ('<w:rPr><w:b/></w:rPr>' if bold else '') + '<w:t xml:space="preserve">' + escape(str(s)) + '</w:t></w:r>'

def p(s='', style='Normal', keep=False):
    body.append(f'<w:p><w:pPr><w:pStyle w:val="{style}"/>' + ('<w:keepNext/>' if keep else '') + '</w:pPr>' + ''.join(text_run(t) + ('<w:r><w:br/></w:r>' if i < len(str(s).split('\n'))-1 else '') for i,t in enumerate(str(s).split('\n'))) + '</w:p>')

def h(s): p(s, 'Heading2')
def note(s): p(s, 'Evidence')
def steps(items):
    for i,s in enumerate(items, 1): p(f'{i:02d}   {s}')

def table(headers, rows, widths=None):
    widths = widths or [int(9920 / len(headers))] * len(headers)
    x = '<w:tbl><w:tblPr><w:tblStyle w:val="EvidenceTable"/><w:tblW w:w="9920" w:type="dxa"/><w:tblLayout w:type="fixed"/><w:tblCellMar><w:top w:w="90" w:type="dxa"/><w:left w:w="100" w:type="dxa"/><w:bottom w:w="90" w:type="dxa"/><w:right w:w="100" w:type="dxa"/></w:tblCellMar></w:tblPr><w:tblGrid>' + ''.join(f'<w:gridCol w:w="{n}"/>' for n in widths) + '</w:tblGrid>'
    for i,row in enumerate([headers]+rows):
        x += '<w:tr><w:trPr><w:cantSplit/>' + ('<w:tblHeader/>' if i==0 else '') + '</w:trPr>'
        for j,cell in enumerate(row):
            shade = '173C4A' if i==0 else ('EEF4F5' if i%2 else 'FFFFFF')
            x += f'<w:tc><w:tcPr><w:tcW w:w="{widths[j]}" w:type="dxa"/><w:shd w:fill="{shade}"/><w:vAlign w:val="top"/></w:tcPr><w:p><w:pPr><w:pStyle w:val="TableText"/></w:pPr>'
            for k,line in enumerate(str(cell).split('\n')):
                if k: x += '<w:r><w:br/></w:r>'
                x += '<w:r><w:rPr>' + ('<w:b/><w:color w:val="FFFFFF"/>' if i==0 else '') + '</w:rPr><w:t xml:space="preserve">' + escape(line) + '</w:t></w:r>'
            x += '</w:p></w:tc>'
        x += '</w:tr>'
    body.append(x+'</w:tbl>')
    p('', 'Spacer')

def page(title, chapter=False):
    if body: body.append('<w:p><w:r><w:br w:type="page"/></w:r></w:p>')
    pages.append(title)
    n = len(pages)
    body.append(f'<w:p><w:pPr><w:pStyle w:val="Eyebrow"/></w:pPr><w:bookmarkStart w:id="{n}" w:name="page{n}"/>{text_run("TECHNICAL ARCHITECTURE  /  VERIFIED REPOSITORY BASELINE")}<w:bookmarkEnd w:id="{n}"/></w:p>')
    p(title, 'Heading1' if chapter else 'Heading2')

def diagram(name, nodes, edges, size=(11.5,6)):
    fig,ax=plt.subplots(figsize=size)
    fig.patch.set_facecolor('white'); ax.set_xlim(0,12);ax.set_ylim(0,7);ax.axis('off')
    for key,(x,y,ww,hh,label,kind) in nodes.items():
        color = '#EDF4F5' if kind!='proposal' else '#FFF3DB'
        ax.add_patch(FancyBboxPatch((x,y),ww,hh,boxstyle='round,pad=0.06,rounding_size=0.10',linewidth=1.2,edgecolor='#285566',facecolor=color,linestyle='--' if kind=='proposal' else '-'))
        ax.text(x+ww/2,y+hh/2,label,ha='center',va='center',fontsize=9.6,color='#173C4A',linespacing=1.45)
    for a,b,label,side in edges:
        x,y,ww,hh,*_=nodes[a]; xx,yy,www,hhh,*_=nodes[b]
        start=(x+ww/2,y+hh/2); end=(xx+www/2,yy+hhh/2)
        if side=='h':
            start=(x+ww if xx>x else x,y+hh/2);end=(xx if xx>x else xx+www,yy+hhh/2)
        elif side=='v':
            start=(x+ww/2,y if yy<y else y+hh);end=(xx+www/2,yy+hhh if yy<y else yy)
        else:
            dx,dy=end[0]-start[0],end[1]-start[1]
            k=min((ww/2+.06)/abs(dx) if dx else 1e9,(hh/2+.06)/abs(dy) if dy else 1e9)
            kk=min((www/2+.06)/abs(dx) if dx else 1e9,(hhh/2+.06)/abs(dy) if dy else 1e9)
            start=(start[0]+k*dx,start[1]+k*dy)
            end=(end[0]-kk*dx,end[1]-kk*dy)
        ax.annotate('',xy=end,xytext=start,arrowprops=dict(arrowstyle='->',lw=1.2,color='#456A77',connectionstyle='arc3,rad=0'))
        mx,my=(start[0]+end[0])/2,(start[1]+end[1])/2
        ax.text(mx,my+0.13,label,ha='center',va='bottom',fontsize=8.1,color='#334C57',bbox=dict(facecolor='white',edgecolor='none',pad=1))
    target=ASSETS/(name+'.png');fig.savefig(target,dpi=190,bbox_inches='tight',pad_inches=.15);plt.close(fig)
    return target

def picture(path, caption, height=3.7):
    i=len(images)+1;rid=f'rIdImage{i}';images.append(path)
    rels.append(f'<Relationship Id="{rid}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/{path.name}"/>')
    from PIL import Image
    with Image.open(path) as im: ratio=im.width/im.height
    width=min(6.85,height*ratio);height=width/ratio
    cx,cy=int(width*914400),int(height*914400)
    body.append(f'<w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="{cx}" cy="{cy}"/><wp:docPr id="{i}" name="{escape(caption)}" descr="{escape(caption)}"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="{i}" name="{path.name}"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="{rid}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="{cx}" cy="{cy}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>')
    p(caption,'Caption')

arch=diagram('logical-architecture',{
 'ui':(.2,4.9,3,1.35,'Angular 22 UI\nBrowser / localhost:4200\nAsk Yaman Ai + finance pages','actual'),
 'core':(4.4,4.9,3.1,1.35,'Spring Boot core-api :8080\nIdentity + finance authority\nVersioned REST /api/v1','actual'),
 'db':(8.6,4.9,3.1,1.35,'H2 relational data\nLocal file / test memory\nFlyway V1–V7','actual'),
 'ai':(4.4,2.35,3.1,1.4,'FastAPI ai-service :8000\nRules + optional model\nConversation ownership','actual'),
 'mongo':(8.6,2.35,3.1,1.4,'MongoDB\nconversations + messages\nAI service owns access','actual'),
 'node':(.2,2.35,3,1.4,'Node integration :3001\nFictional PHP transactions\nNo bank connection','actual'),
 'openai':(4.4,.1,3.1,1.15,'External OpenAI Responses\nOptional configured model\nHTTPS / no tools','actual'),
 'mysql':(8.6,.1,3.1,1.15,'MySQL 8.4 Compose\nDatabase container defined\nCore wiring proposed','proposal')},[
 ('ui','core','same-origin /api proxy','h'),('core','db','JPA + JDBC','h'),('core','ai','internal key + owner¹','v'),('ai','mongo','PyMongo','h'),('core','node','internal key / HTTP',''),('ai','openai','optional SDK call','v')],size=(11.5,6.6))
auth=diagram('authentication-flow',{
 'a':(.2,5.3,3.3,1.05,'Browser boots CSRF\nGET /auth/csrf','actual'),
 'b':(4.25,5.3,3.3,1.05,'POST /auth/login\nemail + password + XSRF','actual'),
 'c':(8.4,5.3,3.3,1.05,'Spring authenticates\nBCrypt + users lookup','actual'),
 'd':(8.4,2.9,3.3,1.15,'Issue 60-minute HS256 JWT\nHttpOnly cookie; rotate CSRF','actual'),
 'e':(4.25,2.9,3.3,1.15,'Browser stores profile only\nGET /auth/session on reload','actual'),
 'f':(.2,2.9,3.3,1.15,'Private API authorization\nJWT + DB token_version','actual'),
 'g':(.2,.4,3.3,1.15,'Expiry or 401\nClear UI; require login','actual'),
 'h':(4.25,.4,3.3,1.15,'Logout: clear cookie\nPassword: revoke all tokens','actual')},[
 ('a','b','','h'),('b','c','','h'),('c','d','','v'),('d','e','profile + expiresIn','h'),('e','f','','h'),('f','g','','v'),('e','h','user action','v')],size=(11.5,6.3))
ai=diagram('finance-chat-flow',{
 'ui':(.2,5.2,3.2,1.15,'Angular conversation UI\nPOST /{id}/messages\nBody contains question only','actual'),
 'core':(4.35,5.2,3.2,1.15,'Spring resolves JWT owner\nRead owned conversation\nRecover stored FINANCE period','actual'),
 'summary':(8.5,5.2,3.2,1.15,'DashboardService + H2\nCurrent monthly aggregates\nAiFinancialRequest mapping','actual'),
 'fast':(4.35,2.7,3.2,1.2,'FastAPI validates context\nLoad last 12 messages\nMode + period consistency','actual'),
 'mongo':(.2,2.7,3.2,1.2,'MongoDB\nOwned metadata + history\nPersist user/assistant pair','actual'),
 'model':(8.5,2.7,3.2,1.2,'OpenAI Responses (optional)\nInstructions + summary\nNo tools; store=False','actual'),
 'fallback':(8.5,.2,3.2,1.25,'FINANCE failure → rules\nGENERAL failure → 503\nNo financial actions','actual'),
 'reply':(4.35,.2,3.2,1.25,'Return answer + provider\n+ disclaimer after save\nUI reloads saved history','actual')},[
 ('ui','core','','h'),('core','summary','','h'),('summary','fast','decimal-string summary',''),('core','fast','key + X-Owner-Id','v'),('mongo','fast','read context','h'),('fast','model','','h'),('model','fallback','','v'),('fallback','reply','','h'),('fast','reply','save then acknowledge²','v')],size=(11.5,6.5))
erd=diagram('data-relationships',{
 'u':(4.3,5.45,3.2,1.05,'users · UUID PK\nemail UNIQUE; token_version','actual'),
 'c':(.2,3.1,3.3,1.25,'categories · BIGINT PK\nuser_id FK; type\nowner/type/name UNIQUE','actual'),
 't':(4.3,3.1,3.2,1.25,'transactions · BIGINT PK\nuser + category + type FK\namount; source; external ID','actual'),
 'b':(8.4,3.1,3.3,1.25,'budgets · BIGINT PK\nexpense-category FK\nowner/category/period UNIQUE','actual'),
 's':(.2,.25,3.3,1.3,'statement_imports → rows\nBatch owner + expiry/status\nNormalized temporary preview','actual'),
 'd':(4.3,.25,3.2,1.3,'demo_connections\ncategory_default_initializations\nOne row per user','actual'),
 'm':(8.4,.25,3.3,1.3,'Mongo: conversations → messages\nownerId = user UUID string\nLogical reference; no SQL FK','actual')},[
 ('u','c','1 : many',''),('u','t','1 : many','v'),('u','b','1 : many',''),('c','t','1 : many','h'),('c','s','owner via users','v'),('u','d','1 : 0..1 each','')],size=(11.5,6.2))

page('YamanTracker', True)
p('System Analysis and\nTechnical Architecture','CoverTitle')
p('Capstone defense • Project handover • Future development','Subtitle')
p('SOURCE-VERIFIED EDITION','Eyebrow')
p('26 October 2026  |  Asia/Manila','Subtitle')
p('Prepared from the actual working tree of the Personal Finance System repository.','Normal')
table(['Document control','Baseline'],[
 ('System','YamanTracker; assistant branded “Ask Yaman Ai”'),
 ('Document version','1.0 — technical analysis and architecture baseline'),
 ('Source revision','b660ac5be99c659bef14953c1b394bfccaa0d616'),
 ('Inspection scope','Angular frontend, Spring core API, FastAPI AI service, Node mock provider, SQL migrations, MongoDB repository, configuration and tests'),
 ('Evidence boundary','Working tree includes pre-existing edits in account-settings documentation/tests and layout files. This document describes inspected files, not only the committed revision.'),
 ('Assessment basis','Static source tracing plus the verification results recorded in section 7. No production environment, live bank, or live model was validated.')],[2500,7420])
p('Intended readers: capstone panel, project owner, developers, testers and deployment maintainers.','Evidence')

page('Contents')
p('The numbered chapters follow the requested analysis structure. Continuation pages expand the inventories, flows and findings. All diagram pathways are implemented unless explicitly marked proposed.')
toc_index=len(body);body.append('TOC_PLACEHOLDER')
h('How to read the evidence')
p('Bracketed evidence IDs, such as [E04], resolve to repository-relative paths and relevant symbols in Appendix A. Source code takes precedence over historical milestone prose. A capability marked Implemented means its code path exists; it does not imply production approval or successful validation in every environment.')
table(['Status','Meaning'],[
 ('Implemented','A connected implementation is present in the inspected source. Runtime limits and test scope still apply.'),
 ('Partial','Some relevant pieces exist, but the stated end-to-end capability or operational readiness is incomplete.'),
 ('Planned','A stated future direction or an explicit UI placeholder; no complete implementation was found.'),
 ('Not verified','Evidence is insufficient to assert the capability, operating state or guarantee.')],[2200,7720])
note('Analysis date is fixed to the user’s client date: 26 October 2026. Versions are repository declarations or local test runtime observations, not claims about the latest public releases.')

page('1. Executive summary and problem statement',True)
p('YamanTracker is a personal finance web application that brings user-owned categories, income and expense transactions, monthly budgets, financial summaries, reviewed CSV imports and contextual AI conversations into one interface. The inspected system is a local-development implementation with substantial application functionality, rather than a verified production financial platform. [E01–E03, E07–E17]')
h('Problem and intended value')
p('Individuals who track spending across notes, spreadsheets and separate account statements must repeatedly classify records, reconcile periods and calculate totals. Fragmentation can make it difficult to see how recorded expenses relate to a monthly budget or to explain why a summary changed. These are design motivations inferred from the implemented workflows; the repository does not provide user research or measured financial outcomes.')
p('The system targets individual account holders who want to record and review their own finances. It centralizes records, calculates monthly aggregates in the core API and supplies a bounded financial summary to Ask Yaman Ai. The assistant explains available data and offers educational budgeting suggestions. It cannot execute payments, retrieve live account balances or verify the completeness of a user’s records. [E07–E11, E14]')
h('Principal objectives')
steps(['Provide authenticated, owner-scoped finance records and account management.', 'Support consistent categorization, transaction management and expense-category budgets.', 'Show reproducible totals, daily spending, category breakdowns and recent records.', 'Explain a selected month using current server-derived context and revisitable conversations.', 'Support reviewed statement import while clearly separating simulation from bank connectivity.'])
h('Current maturity and architectural conclusion')
p('The frontend and three backend processes have distinct runtime boundaries. Spring remains the authority for identity and relational finance data; FastAPI owns rules, model orchestration and MongoDB history; Node supplies fictional transactions. Domain packages inside Spring are internal modules, not independently deployable microservices. [E02, E03, E06, E11–E16]')
p('The near-term handover priorities are to resolve the observed QA failure, make MySQL migrations and configuration executable, define a deployment topology and privacy policy, and strengthen recovery for ambiguous chat writes. Existing cookie/CSRF controls, decimal calculations and owner checks are strengths supported by code. Production availability, real bank integration and financial-outcome improvements remain unverified. [E04–E05, E09, E13, E17–E23]')

page('2. System overview and scope',True)
table(['Capability','Status','Verified boundary / evidence'],[
 ('Registration and login','Implemented','Normalized email, BCrypt, cookie JWT, CSRF and restored browser session. [E04–E05]'),
 ('Profile and password','Implemented','Read profile, change name, verify current password and revoke existing tokens after password change. Email stays read-only in UI. [E05, E18]'),
 ('Categories / transactions / budgets','Implemented','Owner-scoped CRUD, validation, list filtering/paging, monthly budget utilization. [E07–E09]'),
 ('Dashboard / spending summaries','Implemented','Monthly aggregates, daily series, recent five records, top categories; six-month UI history requests. [E09–E10]'),
 ('Ask Yaman Ai and insights','Implemented','FINANCE and GENERAL conversations; rules endpoint; optional OpenAI provider. Live model operation not verified. [E11–E14]'),
 ('Conversation history','Implemented','Owned paginated MongoDB history, last 12 messages as model context. No delete API. [E12–E13]'),
 ('Demo connection / sync','Implemented','Persist simulated connection and import fictional provider records. Displayed balance is hard-coded. [E15–E16]'),
 ('Statement CSV import','Implemented','Map columns, preview, choose categories, confirm/cancel and deduplicate. [E15, E17]'),
 ('Real bank connectivity','Planned','Provider Connect buttons are disabled and labeled coming soon. No verified OAuth, bank API, consent token or live sync. [E15]'),
 ('MySQL main database','Partial','MySQL 8.4 Compose service exists; no MySQL JDBC/Flyway support or application profile found. [E03, E19]'),
 ('Cloud deployment / CI/CD','Not verified','No application Dockerfiles, deployment manifests, AWS infrastructure or build workflow found in inspected tree. [E19, E23]')],[2350,1450,6120])
h('Actors and boundaries')
p('Primary actor: an authenticated individual user. A visitor registers and signs in. Maintainers configure processes and databases using documented local commands; no administrator UI or role-based administration was found. Spring acts as the trusted internal caller to AI and mock services. The optional model provider is an external dependency, not an application user. [E01–E06, E11–E16]')
p('Out of current scope: payments, money transfers, bank credential collection, verified account aggregation, investment execution, multi-currency conversion, household sharing and professional financial advice. Some may be future product choices; none should be represented as implemented.')

page('2.1 Requirements and supported workflows')
table(['ID / requirement','Status','Acceptance behavior derived from source'],[
 ('FR01 Identity and session','Implemented','Register without automatic login; login and restore safe user profile; logout and expiry clear browser state. [E04–E05]'),
 ('FR02 Categorize records','Implemented','INCOME/EXPENSE categories, optional color, one-time defaults; reject changes/deletes that violate references. [E07]'),
 ('FR03 Maintain transactions','Implemented','Positive monetary amount, same-owner/type category, CRUD and paginated search. [E08]'),
 ('FR04 Set monthly budgets','Implemented','One expense-category budget per user/month/year; amount, threshold and derived status. [E09]'),
 ('FR05 Review finances','Implemented','Select period; compute summaries from stored transactions rather than the current UI page. [E09–E10]'),
 ('FR06 Ask / revisit assistant','Implemented','Create mode-specific conversation, send question, reload saved history; require a valid provider for GENERAL answers. [E11–E14]'),
 ('FR07 Import records','Implemented','Review CSV mappings and category choices; demo sync imports mock records. No live banking. [E15–E17]'),
 ('NFR01 Data isolation','Implemented controls','Verified JWT owner scopes relational operations and trusted internal conversation calls. No independent penetration audit asserted. [E04, E07–E13]'),
 ('NFR02 Monetary consistency','Implemented controls','BigDecimal / Decimal; bounded inputs; exact status comparisons. Browser public responses remain JSON numbers. [E08–E10, E14]'),
 ('NFR03 Resilience / usability','Partial','Timeouts, safe errors, loading/error states, mobile/theme implementation and tests; no SLA or accessibility certification. [E10–E18, E20]'),
 ('NFR04 Production operations','Not verified','No measured throughput, deployment readiness, restore exercise, managed-secret integration or operational runbook proven. [E19–E23]')],[2350,1700,5870])
h('Workflow and scope assumptions')
p('A typical user signs in, accepts or edits default categories, records transactions, sets monthly expense budgets, reviews summaries, and asks a question in a FINANCE conversation for a fixed month. CSV records enter the same transaction ledger after explicit review. Updates trigger UI finance refreshes. [E07–E18]')
p('The UI and import/provider pathways use PHP. Core transactions do not carry a currency column, so the architecture does not implement a general multi-currency ledger. The intended target population and currency policy should be confirmed with stakeholders. Requirements above are reconstructed from code, not a signed requirements specification.')

page('3. High-level architecture',True)
picture(arch,'Figure 1. Verified logical architecture; amber dashed MySQL box indicates an unwired application target.',4.45)
p('The browser uses relative /api/v1 calls. Angular’s development proxy forwards /api to localhost:8080. Private application routes use client rendering; public pages have prerender configuration. An Express SSR/static server entry exists and defaults to port 4000, but it does not implement the production /api reverse proxy. That proxy is a deployment requirement. [E02]')
p('Spring calls FastAPI and Node synchronously through dedicated RestClient beans with distinct internal credentials and timeouts. The browser has no implemented direct connection to these services. Spring builds financial aggregates from its relational store; FastAPI has no relational-database client. [E06, E11–E16]')
note('¹ X-Owner-Id is supplied for conversation routes; stateless insights/chat use a minimal financial body. The owner header is trusted only with the internal key. MongoDB references user identity logically, without a cross-database foreign key. No queue, service mesh or independent authentication service was found.')
h('Deployment interpretation')
p('There are four application deployable units: frontend, core-api, ai-service and integration-service. MySQL and MongoDB are infrastructure services in Compose. H2 runs within the Java process. Separately executable backend services do not make each Java package a microservice; the core API is a modular monolith behind a single application boundary.')

page('3.1 Deployable service inventory')
table(['Unit / interface','Responsibility and owned data','Dependencies and failure considerations'],[
 ('Angular frontend\nng serve :4200; optional generated Express host :4000','Routes, forms, dashboard rendering, session-aware state, finance API clients and floating assistant. Holds transient profile/finance/chat UI state; no persistent ledger.','Depends on same-origin core API and proxy. Auth restoration errors have a session-error route; 401 invalidates state. API transfer caching disabled for private calls. [E02, E05, E10, E18]'),
 ('Spring core-api\nREST :8080; /api/v1; /actuator/health','Identity and token issuance; relational ownership; finance CRUD, aggregation, imports, profile/password; AI orchestration gateway. Owns users and all relational tables.','H2 local/test, configured JWT secret; optional outbound AI/mock availability. DB outage affects identity and finances. AI/mock failures are sanitized and do not inherently remove CRUD. [E03–E12, E15–E17]'),
 ('FastAPI ai-service\nUvicorn :8000; /api/v1/insights and /chat','Deterministic insights, FINANCE/GENERAL chat, OpenAI orchestration and owned conversation history. Owns MongoDB conversations/messages.','Internal key required at startup. Mongo is lazy and operation-bounded. Provider optional: FINANCE falls back; GENERAL fails with 503. History routes require Mongo. [E11–E14]'),
 ('Node integration-service\nExpress :3001; /internal/mock-transactions','Normalizes a deterministic fictional PHP fixture. No user database or bank tokens; receives no user-owner query.','Separate internal key, loopback listener, fixed payload validation; Spring rejects invalid upstream envelopes. It is a mock adapter, not a bank aggregator. [E16]')],[2250,3900,3770])
h('Infrastructure inventory')
p('H2 stores the active local relational ledger as core-api/data/personal-finance; tests use memory. MongoDB owns independent conversation documents. Compose defines mongo:7 and mysql:8.4 with named volumes, loopback-published ports and database health checks. Compose does not launch the applications. A volume is persistence configuration, not a backup policy or demonstrated restore capability. [E03, E19]')

page('3.2 Repository structure and internal modules')
table(['Repository location','Role and deployment interpretation'],[
 ('frontend/personal-finance-ui/','Single Angular workspace: src/app/core holds HTTP services, models, guards and interceptor; features holds finance/auth/account/bank pages; layouts provides shell; shared/components/ai-assistant provides chat. No separately deployed shared library found. [E02, E18]'),
 ('core-api/src/main/java/core_api/','One Spring Boot application. Packages auth, security and user handle identity/account; category, transaction and budget handle ledger operations; dashboard aggregates; insights and chat call AI; integration calls Node; bank implements demo/CSV; error/config provide cross-cutting behavior. [E03–E17]'),
 ('core-api/src/main/resources/','Base/local properties and Flyway db/migration/V1–V7. Maven wrapper, pom.xml and src/test comprise the build and test boundary. [E03, E17, E20]'),
 ('ai-service/app/','main.py composes FastAPI; api contains routers and trusted-header dependencies; schemas contains strict Pydantic contracts; services contains rules, provider, chatbot and Mongo repository; core contains environment config and Mongo lifecycle. [E11–E14]'),
 ('integration-service/src/','Express app/server, config, internal-auth middleware, mock routes, fixtures and provider normalizer; tests/service.test.js verifies contract. [E16]'),
 ('qa/ and frontend/.../qa/','Cross-service, cookie/browser and Bruno collection validation. Opt-in harness launches isolated services with test databases/provider substitutes. These are tooling, not runtime services. [E20–E21]'),
 ('docs/, README.md, service READMEs, Bruno folders','Milestone reports, manual requests and samples. Useful context, but some old descriptions are superseded by code. [E01, E23]'),
 ('docker-compose.yml; .github/modernize/','Two database containers; Java modernization hook scripts. No application deployment or CI pipeline inferred from the .github directory alone. [E19, E23]')],[3000,6920])
h('Configuration boundary')
p('Application secrets are environment-driven with ignored local .env support in Python and Node. Spring does not automatically load the root .env. Dedicated AI and integration client configurations set base URLs and connect/read timeouts. Internal modules share the same Java process and database transactions; changes to them ship with the core-api artifact. [E01, E03, E06]')

page('4. Registration, login and token lifecycle',True)
picture(auth,'Figure 2. Implemented browser authentication and session lifecycle.',3.55)
steps(['Angular obtains the readable CSRF cookie with GET /api/v1/auth/csrf. Registration sends name, normalized email and password with the XSRF header; Spring validates and hashes the password, stores the user and returns 201 UserResponse. Registration does not issue a login cookie.', 'Login bootstraps CSRF again, authenticates through DaoAuthenticationProvider/BCrypt and locks the account lookup against concurrent password changes. TokenService issues a 3,600-second HS256 JWT with subject UUID, issuer, audience, times, jti and token_version.', 'AuthController sets the HttpOnly session cookie, rotates CSRF and returns only {user, expiresIn}. Angular stores profile and an expiry timer in memory, not the JWT. On reload, GET /auth/session restores the profile and remaining lifetime.', 'Private requests validate signature/claims and check that the user exists and the stored token version equals the claim. Cookie mutations require CSRF. Header-only Bearer access outside auth lifecycle routes remains supported; mixed cookie/header credentials are rejected.', 'Logout clears the current cookie and rotates CSRF; it does not increment token_version. A copied token can remain usable until expiry. Password change increments token_version and invalidates all earlier tokens. No refresh-token flow was found.'])
note('Evidence: [E04–E05]. Secure __Host-finance_session is the default; the explicit local profile uses finance_session with Secure=false for HTTP. Zero JWT clock skew is configured. Frontend BroadcastChannel notifications coordinate session changes across tabs.')

page('4.1 Dashboard and spending summary flow')
steps(['The protected dashboard route waits for AuthService session restoration. DashboardStore selects the browser’s current month/year by default, tracks the current owner and cancels obsolete requests on session/period changes.', 'The store requests GET /api/v1/dashboard?month=…&year=… for the selected month and up to five other months in its six-month display. It separately loads category labels. These are ordinary REST requests, not a scheduled analytics job.', 'Spring derives the UUID from the authenticated principal. DashboardController accepts only month/year parameters; BudgetPeriod bounds month to 1–12 and year to 2000–2100.', 'DashboardService queries owner-scoped category aggregates, daily expense totals and the most recent five transactions using an inclusive month start and exclusive next-month boundary. It asks BudgetService for utilization.', 'FinancialSummary derives income, expenses, savings and savings rate. DashboardResponse includes expense/income categories, a daily series with missing dates filled as zero, recent records, budget utilization and the top five expense categories.', 'Angular validates report period and supported numeric ranges, maps the data into cards/charts, and shows per-request errors rather than fabricating zeros for unavailable months. Successful finance mutations emit FinanceChanges so reports reload.'])
h('Calculation contract')
table(['Measure','Implemented calculation / edge behavior'],[
 ('Net savings','totalIncome − totalExpenses. A negative result is retained.'),
 ('Savings rate','netSavings × 100 ÷ totalIncome, 2 decimals HALF_UP; null when income is zero.'),
 ('Budget remaining','monthlyLimit − spentAmount; may be negative.'),
 ('Budget percentage','spentAmount × 100 ÷ monthlyLimit, 2 decimals HALF_UP; may exceed 100%.'),
 ('Budget status','EXCEEDED when spent ≥ limit (including exactly 100%); otherwise WARNING when spent × 100 ≥ limit × alertThreshold; otherwise SAFE.'),
 ('Browser presentation','Public Java money is JSON numeric. Dashboard rejects nonfinite money or absolute values above 10¹²; chart percentage allocation uses integer cents / BigInt remainders.')],[2200,7720])
note('Evidence: [E09–E10]. All financial summary totals originate from Spring repository aggregates, not from the frontend’s paginated transaction list. Displayed savings is recorded net cash flow, not a verified bank account balance.')

page('4.2 Transaction, category and budget workflows')
h('Recording and managing transactions')
steps(['Angular loads the owner’s categories, opens an add/edit form and submits categoryId, type, positive amount, optional description and transactionDate. UI and backend validate fields; backend remains authoritative.', 'TransactionService locks the owner row, resolves the category with findByIdAndUser_Id and requires matching INCOME/EXPENSE type. Save/update runs in a relational transaction; SQL composite foreign keys also enforce category/owner/type consistency.', 'Create returns 201; update/read returns the transaction DTO. Delete first resolves ownership, then returns 204. Accessing another owner’s record returns a not-found response.', 'List requests accept type, categoryId, inclusive start/end date filters, keyword, page, size, sort and direction. The query bounds size to 1–100 and adds ID ordering for deterministic ties. Successful writes trigger FinanceChanges and reload affected views.'])
h('Category lifecycle')
p('CategoryService.list and create initialize defaults once under an owner lock, using category_default_initializations independently of category names. Defaults include Salary/Freelance and Food/Housing/Transportation. Thus a GET category list may cause initialization writes. Names are normalized for per-owner/type uniqueness. A referenced category cannot be deleted or changed to an incompatible type. [E07]')
h('Monthly budgets')
steps(['The user chooses an EXPENSE category, month/year, positive monthlyLimit and integer alertThreshold from 1–99. BudgetService enforces one budget per category/user/period.', 'BudgetService reads actual expense sums for the same owner/category/month. BudgetUtilization derives spent, remaining, percentage and SAFE/WARNING/EXCEEDED status on demand.', 'Budgets support list/create/read/update/delete. Recorded transaction edits affect the next utilization calculation; no separate mutable “spent total” is stored. UI refresh signals update active views.'])
h('Restrictions and operational implications')
p('There are no verified recurring-transaction schedules, automated budget enforcement, email/push alerts or payment operations. “Alert threshold” currently means a computed UI/response status. Owner-row locks serialize related writes; this protects integrity but may limit throughput for heavy imports on one account. [E07–E09]')
note('Evidence: [E07–E10, E18]. Transaction monetary storage is DECIMAL(19,2); API request limits are 17 integer digits and at most 2 fractional digits. The browser imposes a smaller supported display range.')

page('4.3 Asking Ask Yaman Ai')
picture(ai,'Figure 3. Verified persisted FINANCE conversation path. ² Persistence uses compensation, not an atomic MongoDB transaction.',4.45)
p('The floating assistant uses conversation endpoints, not the stateless /chat/messages endpoint. A new conversation stores GENERAL or FINANCE mode, optional title, and month/year for FINANCE. Existing conversation sends accept only a question from Angular; ownership, stored mode/period and finance context are recovered server-side. [E11–E13, E18]')
p('For FINANCE, Spring obtains an owned conversation before reading financial data, then builds a fresh AiFinancialRequest from DashboardService. The minimal aggregate contains totals, expense category amounts and budget limits/spending/thresholds. It omits raw transactions, profile/email, credentials, daily trend and account balances. Amounts cross the internal API as decimal strings. [E11–E12]')
p('FastAPI validates the matching period and aggregate consistency, retrieves bounded history, invokes the optional provider or FINANCE fallback, then saves the user/assistant pair and updates conversation recency. The successful response includes answer, provider and disclaimer; a storage failure is not acknowledged as a confirmed saved turn. [E13–E14]')

page('4.4 Prompt, fallback and revisiting history')
h('Model request and response')
p('OpenAIChatProvider uses AsyncOpenAI at https://api.openai.com/v1 and the Responses API. The model identifier comes from OPENAI_MODEL; no specific live model is established by the source. Requests specify mode-specific instructions, an untrusted-history input when present, the current question/financial summary, max_output_tokens, store=False and tools=[]. SDK retries are disabled. [E14]')
p('FINANCE instructions limit claims to the supplied month, require acknowledging missing data, reject invented balances/transactions and forbid financial actions. Category IDs are removed from model-facing expense entries; names and monetary strings remain. History is serialized as untrusted context, not authoritative financial evidence. These are prompt controls, not a mathematical guarantee that a model answer is correct. Output must be nonblank, completed and at most 8,000 characters. [E14]')
h('Modes and deterministic behavior')
table(['Path','Behavior'],[
 ('GET core /insights → POST AI /insights/analyze','Pure deterministic rules: monthly summary, savings threshold of 20%, highest expense category, illustrative 10% reduction and budget conditions. No model request or history write.'),
 ('FINANCE conversation / stateless FINANCE chat','Attempt configured provider. On missing/failing/invalid output, use rule_based_answer. The fallback checks a limited English question pattern and excludes unsupported subjects; it is not an unrestricted conversational model.'),
 ('GENERAL conversation / stateless GENERAL chat','Send question/history without financial summary. A missing or unavailable provider yields 503 CHAT_UNAVAILABLE; no rule-based GENERAL substitute exists.')],[3400,6520])
h('Revisiting a conversation')
steps(['Angular requests the owned conversation list (default 20 per page), sorted by updatedAt and ID descending.', 'Selecting an item requests GET /chat/conversations/{id}. Messages are embedded, ordered by createdAt/ID ascending; there is no separate GET /messages endpoint. Angular history() loads 100-message pages until a short page is reached.', 'A subsequent turn receives the most recent 12 messages as model context. FINANCE totals are refreshed from the stored period on every send. Reopening history reads saved text without rerunning the model.', 'On timeout or uncertain persistence, the UI preserves the draft, reloads history and can block resending in that conversation to avoid duplicates. No idempotency-key protocol or conversation deletion API is present.'])
note('Evidence: [E11–E14, E18]. Concurrent sends can read the same prior context; Mongo writes use best-effort rollback. Historical answer text may reflect earlier ledger values because only the answer/history, not a full financial snapshot, is persisted.')

page('4.5 Account management and bank UI behavior')
h('Profile and password')
steps(['The authenticated Account Settings route calls GET /api/v1/users/me to load safe profile fields. Email and creation date are read-only; editing submits only {name} through PUT /users/me.', 'UserService acquires an account-update lock and rechecks token_version before changing the name. Angular updates current-user state only if session revision and user ID still match.', 'Password change submits {currentPassword,newPassword} through PUT /users/me/password with CSRF. UserService verifies the old hash, rejects reuse, hashes the new password and increments token_version within the transaction.', 'On 204, the controller clears the authentication cookie and rotates CSRF. Angular clears form/session state, notifies other tabs and navigates to login. Earlier cookies/Bearer tokens fail future database version checks. No password recovery, email change or MFA workflow was found.'])
h('Real provider cards versus simulation')
p('The Bank Connections screen lists BPI, BDO, Metrobank, Security Bank, UnionBank and Other Bank. Provider-card Connect buttons are explicitly disabled and labeled COMING SOON, except that the simulated provider can show DEMO CONNECTED. These controls do not call real bank APIs. [E15]')
steps(['A separate demo action sends POST /bank-connections/demo and stores connected=true for the verified owner. DELETE /demo sets it false; existing imported records are not deleted.', 'POST /demo/sync requires an active demo connection. Spring calls the separate Node provider, validates each normalized fictional transaction and imports under the authenticated user.', 'MockImportService initializes needed defaults and uses one REQUIRES_NEW transaction per provider row. Existing (owner, MOCK_PROVIDER, external ID) records are skipped; invalid rows increment failedCount.', 'The connection response is simulated=true and returns a hard-coded PHP demoBalance of 128954.75 with a masked sample account. lastSync is recorded after the import operation returns; it does not prove every row succeeded.'])
note('Evidence: [E05, E15–E16, E18]. The real bank “Connect” lead is confirmed as a disabled placeholder, but the separate demo workflow and CSV import are functional implementations. No bank credentials, OAuth exchange, automatic bank polling or live account data are verified.')

page('4.6 Reviewed statement import and cleanup')
steps(['The user selects/drops one UTF-8 CSV file. UI and server cap size at 1 MiB; the parser permits a header plus 1–1,000 data rows, at most 50 columns and 4,096 characters per field.', 'POST /bank-connections/statements/columns returns headers. The user maps date/description and either signed amount or debit/credit columns, optional reference/currency, explicit ISO/DMY/MDY date order and a stable account label. Only PHP statements are supported.', 'POST /statements/preview parses and validates rows, locks the owner and cancels earlier active previews for that owner. It stores normalized rows in H2 and returns row errors/duplicates with a 30-minute expiration; raw file bytes are not retained by the workflow.', 'Angular suggests categories by a simple description/name match but requires an explicit category selection for each included valid row. Invalid or known duplicate rows are excluded. The preview is not an AI categorization service.', 'POST /statements/{id}/confirm accepts selected row numbers/category IDs, checks ownership, status/expiry, type-compatible owned categories and deduplication. It writes CSV_STATEMENT transactions and the receipt transactionally, then deletes preview rows. Reconfirming a confirmed batch returns its recorded counts.', 'DELETE /statements/{id} cancels an unconfirmed preview and removes its rows. StatementCleanup invokes purgeExpired every 60 seconds of fixed delay; it expires stale previews and prunes non-preview receipts whose expires_at is older than 30 days. Cleanup requires the core process to be running.'])
h('Duplicate detection and reconciliation limits')
p('Each CSV external ID is a SHA-256 digest based on the account label and mapped bank reference, or, without references, the file digest plus row number. Unique (user, source, external ID) storage prevents known repeat imports. Without references, different overlapping exports are not reliably deduplicated; matching date, amount and description alone does not prove a duplicate. This is reviewed import with limited duplicate detection, not full account reconciliation. [E15, E17]')
p('The confirmation skippedCount includes excluded rows as well as duplicates. A whole CSV confirmation is transactional, unlike per-row mock imports. Raw uploads stay in memory under the configured multipart threshold, but normalized preview descriptions and amounts remain database content until removed. Backups and transaction history retention are not governed by the preview cleanup job. [E03, E15, E17]')
note('Evidence: BankController, BankService.preview/confirm/cancel/purgeExpired, StatementCsv and StatementCleanup [E15, E17]. The cleanup SQL uses H2 DATEADD; a MySQL migration requires application SQL changes as well as DDL review.')

page('5. API inventory — identity and finance',True)
p('All paths in this table are owned by Spring and prefixed /api/v1. “U” means a verified cookie JWT or header-only Bearer JWT. “P” means public authorization; unsafe browser requests still require CSRF, including registration/login/logout. Source references identify controllers and corresponding service/repository reads and writes. [E04–E12]')
table(['Method / path suffix','Auth','Request → response purpose','Data access / evidence'],[
 ('GET /auth/csrf','P','Bootstrap readable XSRF cookie; no JSON profile.','CSRF repository only. [E04]'),
 ('POST /auth/register','P + CSRF','{name,email,password} → 201 UserResponse.','Insert users; BCrypt hash. [E05]'),
 ('POST /auth/login','P + CSRF','{email,password} → {user,expiresIn}; HttpOnly cookie.','Read/lock users; issue JWT. [E04–E05]'),
 ('GET /auth/session','U','Safe profile + remaining expiresIn; restore CSRF if needed.','Read users; no refresh token. [E05]'),
 ('POST /auth/logout','P + CSRF','Clear auth cookie, rotate CSRF → 204.','No token-version update. [E05]'),
 ('GET /users/me','U','Return id/name/email/createdAt/updatedAt.','Read users by authenticated UUID. [E05]'),
 ('PUT /users/me','U','{name} → safe updated profile.','Lock/update users. [E05]'),
 ('PUT /users/me/password','U','{currentPassword,newPassword} → 204 and sign-out.','Lock/update hash + token_version. [E05]'),
 ('GET /categories','U','Optional type → category array; initializes defaults once.','Read/write categories + default marker. [E07]'),
 ('POST /categories','U','{name,type,color} → 201 category.','Insert category, seed if needed. [E07]'),
 ('PUT /categories/{id}\nDELETE /categories/{id}','U','Replace mutable fields → category; delete → 204.','Owned category; reference checks. [E07]'),
 ('GET /transactions','U','Filters/paging → TransactionPageResponse.','Owner-scoped specifications. [E08]'),
 ('POST /transactions','U','{categoryId,type,amount,description,transactionDate} → 201.','Owned category + insert transaction. [E08]'),
 ('GET /transactions/{id}\nPUT /transactions/{id}\nDELETE /transactions/{id}','U','Read / replace fields / remove → DTO or 204.','Owned transaction; writes under owner lock. [E08]')],[2600,1100,3370,2850])
note('Cookie-based PUT/POST/DELETE require X-XSRF-TOKEN. Header-only Bearer compatibility bypasses CSRF only outside /auth lifecycle routes. There is no GET /categories/{id} endpoint. UUID identifies users; BIGINT identifies finance records.')

page('5.1 API inventory — budgets, chat and imports')
table(['Method / /api/v1 suffix','Auth','Contract purpose','Data access / evidence'],[
 ('GET /budgets','U','Required month/year → budget DTO array including utilization.','budgets + expense sums. [E09]'),
 ('POST /budgets','U','{categoryId,month,year,monthlyLimit,alertThreshold} → 201.','Owned expense category; insert. [E09]'),
 ('GET /budgets/{id}\nPUT /budgets/{id}\nDELETE /budgets/{id}','U','Read / update same request shape / delete → DTO or 204.','Owned budgets; derived spend. [E09]'),
 ('GET /dashboard','U','Required month/year → DashboardResponse.','transactions + categories + budgets. [E10]'),
 ('GET /insights','U','Required month/year → structured educational insights.','SQL aggregates; POST AI analyze. [E11]'),
 ('POST /chat/messages','U','{question,mode,month,year}; FINANCE needs period → answer/provider/disclaimer.','SQL summary for FINANCE; AI /respond; no history write. [E11]'),
 ('GET /chat/conversations\nPOST /chat/conversations','U','List page/size; create {mode,title?,month?,year?} → metadata.','Spring gateway → Mongo via AI. [E12–E13]'),
 ('GET /chat/conversations/{id}','U','page/size → metadata + messages + disclaimer.','Owned conversation and history. [E12–E13]'),
 ('POST /chat/conversations/{id}/messages','U','{question} → saved turn answer/provider/disclaimer.','Read owned metadata; fresh FINANCE SQL summary; Mongo pair write. [E12–E14]'),
 ('POST /transactions/import/mock','U','No body/query allowed → imported/skipped/failed/total counts.','Node fixture → per-row SQL writes. [E16]'),
 ('GET /bank-connections','U','Simulated connection state.','demo_connections; hard-coded demo metadata. [E15]'),
 ('POST /bank-connections/demo\nDELETE /bank-connections/demo','U','Connect/disconnect simulated account → state.','Insert/update demo_connections. [E15]'),
 ('POST /bank-connections/demo/sync','U','Connected demo required → import result.','Node → transactions; update last_sync. [E15–E16]')],[3150,650,3200,2920])
p('Conversation IDs are 24 hexadecimal characters. Page/size defaults are 0/20 for listing and 0/100 for reads, with size 1–100 and page ≤100000. Conversation creation currently returns 200, not an assumed 201. FINANCE mode stores its period; GENERAL creation rejects a financial period in FastAPI. [E12–E13]','Evidence')

page('5.2 API inventory — CSV, internal and operational')
table(['Owner / method / endpoint','Auth','Purpose and storage'],[
 ('Spring POST /api/v1/bank-connections/statements/columns','U + CSRF','multipart file → header array. Parse only; no preview persistence.'),
 ('Spring POST /api/v1/bank-connections/statements/preview','U + CSRF','multipart file + Mapping fields → batch ID, expiry, headers and normalized rows. SQL preview writes.'),
 ('Spring POST /api/v1/bank-connections/statements/{id}/confirm','U + CSRF','{rows:[{rowNumber,categoryId}]} → counts; SQL transaction writes and preview row deletion.'),
 ('Spring DELETE /api/v1/bank-connections/statements/{id}','U + CSRF','Owned cancellation → 204; delete normalized rows, mark receipt CANCELLED.'),
 ('FastAPI POST /api/v1/insights/analyze','Internal key','FinancialSummary decimal-string contract → summary, positives, concerns, recommendations, suggested actions, disclaimer. No DB or model call.'),
 ('FastAPI POST /api/v1/chat/respond','Internal key','Question/mode plus FINANCE period/summary when needed → ChatResponse. Optional model or fallback; no history persistence.'),
 ('FastAPI GET / POST /api/v1/chat/conversations','Internal key + owner','List pagination / create metadata. Reads or writes Mongo conversations.'),
 ('FastAPI GET /api/v1/chat/conversations/{conversation_id}','Internal key + owner','Read paginated owned metadata and messages.'),
 ('FastAPI POST /api/v1/chat/conversations/{conversation_id}/messages','Internal key + owner','Question + fresh matching FINANCE summary when required; read history, answer and persist pair.'),
 ('Node GET /internal/mock-transactions','Separate internal key','No query parameters; {transactions:[normalized fictional records]}; no database writes.'),
 ('Spring GET /actuator/health\nFastAPI GET /health\nNode GET /health','Public','Operational health endpoints. FastAPI/Node static liveness responses do not establish provider/Mongo readiness.'),
 ('Spring /swagger-ui.html, /v3/api-docs\nFastAPI /docs, /redoc, /openapi.json','Public docs','Contract discovery; not a separate application service.')],[4800,1450,3670])
note('Sources: [E03–E04, E11–E17]. Internal key header is X-Internal-API-Key; conversation owner is X-Owner-Id (UUID). End users must use Spring; internal services trust the authorized Spring caller instead of validating user JWTs themselves. No undocumented conversation delete/update/message-list endpoint is assumed.')

page('5.3 Data design and relationships')
picture(erd,'Figure 4. Logical data relationships. SQL integrity is local to H2; Mongo ownership is enforced by repository filters.',4.15)
p('Spring is the only implemented authority for the relational ledger. Most domain operations use JPA repositories; BankService uses JdbcTemplate for demo/preview bookkeeping and JPA repositories for final transactions. Both participate in Spring’s relational transaction boundary. Foreign keys and service lookups enforce category ownership/type. [E07–E09, E15, E17]')
p('FastAPI owns conversations and messages through ConversationRepository. The ownerId string corresponds to the relational users UUID, but MongoDB cannot enforce that relationship with a SQL foreign key. Account deletion and cross-store erasure orchestration are not implemented. Serialized history omits ownerId from the public payload. [E12–E13]')
note('Figure omits some edges for readability: every relational user-owned table references users; transactions and budgets also reference their categories; statement_rows uses (batch_id,row_number) as a compound primary key. Detailed constraints follow.')

page('5.4 Relational schema and migration sequence')
table(['Migration / table','Key design and constraints'],[
 ('V1 users','UUID PK; name ≤100; normalized unique email ≤254; password_hash VARCHAR(60); creation/update timestamps. V7 adds nonnegative token_version for revocation.'),
 ('V2 categories','BIGINT identity; user FK; INCOME/EXPENSE; normalized_name unique per user/type; optional validated hex color. Composite (id,user_id,type) unique key supports transaction and budget foreign keys.'),
 ('V2 category_default_initializations','user_id PK/FK and initialized_at. Tracks one-time default seeding even after category deletion or renaming.'),
 ('V3 transactions','BIGINT identity; user FK; composite category_id/user_id/type FK; positive DECIMAL(19,2); description ≤255; transaction_date DATE; source; timestamps. Owner/date/type/category indexes.'),
 ('V4 budgets','BIGINT identity; user + EXPENSE category relationship; quoted month/year; positive DECIMAL(19,2) monthly_limit; alert_threshold 1–99; unique owner/category/period.'),
 ('V5 mock import additions','transactions.source expands; external_transaction_id VARCHAR(100); unique (user_id,source,external_transaction_id). MANUAL requires null external ID; mock records require an ID.'),
 ('V6 demo_connections','user_id PK/FK; connected flag and last_sync. Sample institution/account/balance are not database-backed bank account facts.'),
 ('V6 statement_imports','UUID batch PK; owner FK; expires_at; PREVIEW/CONFIRMED/CANCELLED/EXPIRED; imported/skipped/total counts. Indexed status/expiry for cleanup.'),
 ('V6 statement_rows','Compound batch_id/row_number PK; batch FK; normalized date, description, type, amount, external_id, validation_error. Temporary rows removed after confirm/cancel/expiry.'),
 ('V6 transaction source extension','CSV_STATEMENT allowed with nonblank external ID. Same source/owner deduplication constraint supports reviewed imports.')],[3200,6720])
h('Migration discipline and portability')
p('Flyway owns schema evolution; Hibernate ddl-auto=validate and spring.sql.init.mode=never prevent a second schema writer. clean is disabled and baseline-on-migrate is false. Applied migrations should remain immutable; add versioned migrations instead. [E03, E17]')
p('Existing DDL contains H2-specific UUID/identity/timestamp syntax, REGEXP_LIKE usage and quoted identifiers. BankService also uses DATEADD and expects uppercase JDBC map keys. MySQL readiness requires verified dialect/schema/application-SQL changes, driver support and migration tests; changing only a JDBC URL is insufficient. [E03, E15, E17]')

page('5.5 MongoDB design, payloads and environments')
table(['Store / collection','Shape and ownership'],[
 ('Mongo conversations','_id ObjectId; ownerId UUID string; mode; title; FINANCE month/year; createdAt, updatedAt. Index: ownerId ASC, updatedAt DESC, _id DESC.'),
 ('Mongo messages','_id ObjectId; conversationId ObjectId; role user/assistant; content; provider user/openai/rule-based; createdAt. Index: conversationId ASC, createdAt ASC, _id ASC.'),
 ('History access','Read parent with both _id and ownerId before message access. context() selects newest 12 messages then reverses into chronological order. read() provides paginated full history.'),
 ('Turn persistence','insert_many(user+assistant) then update parent updatedAt with $max. On failure, attempt deleting inserted message IDs. This is best-effort compensation; no multi-document transaction.')],[2400,7520])
table(['Environment / config','Relational database','Conversation store / limits'],[
 ('Explicit local profile','jdbc:h2:file:./data/personal-finance; sa/blank password; H2 console enabled locally.','MONGODB_URI default local unauthenticated URI; database default personal_finance_ai. Compose credentials must be supplied in URI when using its authenticated Mongo.'),
 ('Automated Java tests','In-memory H2; test configuration + test fixtures.','AI HTTP contracts generally mocked in core tests; not a real database integration.'),
 ('Python and cross-service tests','Python itself has no relational persistence; isolated E2E harness can start H2 core.','mongomock used; real Mongo test opt-in. Provider mocked where specified.'),
 ('Base / intended production','No explicit MySQL datasource profile; H2 runtime dependency exists and embedded auto-configuration may apply if run without local settings. Not a production configuration.','Secure cookie default exists; no verified production database URI, topology, backup, TLS or retention policy.'),
 ('Docker Compose infrastructure','mysql:8.4 container only; no MySQL connector or application wiring in pom.xml.','mongo:7 with root credentials, volumes and healthcheck; application services are started separately.')],[2450,3500,3970])
p('Public finance DTOs use JSON numbers and ISO-style dates/timestamps; internal money uses decimal strings. FinancialSummary in Python rejects unknown fields, invalid totals/rates, duplicate category IDs and summaries that exceed list/size limits. Chat questions are ≤1,000 characters, responses ≤8,000; FINANCE chat context is ≤32 KiB. These are application contract limits, not an assertion of unlimited scalability. [E08–E14]','Evidence')

page('6. Technology stack and recorded versions',True)
table(['Layer / technology','Recorded version','Purpose / status / evidence'],[
 ('Angular; Material/CDK; SSR','Angular ^22.2.0; Material/CDK/SSR ^22.2.4','Frontend framework/forms/router/components and public rendering scaffolding. Package ranges, not all exact resolved versions. [E02]'),
 ('TypeScript / RxJS','~6.0.2 / ~7.8.0','Typed client and observable request/state flows. Implemented. [E02, E10, E18]'),
 ('Frontend npm / Express host','npm@11.11.0; Express ^5.1.0','Build/package manager declaration and optional generated server host. Host deployment not verified. [E02]'),
 ('Java / Spring Boot','Java 25; Boot parent 4.1.1','Core runtime and web/JPA/security/validation/actuator starters. Runtime test observed Java 25.0.4.1 where recorded. [E03]'),
 ('Spring Security / Hibernate / Flyway / H2','Boot-managed; no explicit independent versions in pom','Security/resource server, ORM, versioned schema, embedded relational DB. Active local/test stack. [E03–E04, E17]'),
 ('springdoc / Maven','springdoc 3.1.1; Maven wrapper distribution 3.10.0','OpenAPI/Swagger UI and Java build. [E03]'),
 ('Python / FastAPI / Uvicorn','Python 3.14 documented; 0.150.0 / 0.54.0 pinned','AI REST service and ASGI server. Python requirement is in setup prose, not a pyproject requires-python constraint. [E01, E14]'),
 ('Pydantic / PyMongo / dotenv','2.14.1 / 4.19.0 / 1.2.4 pinned','Strict contracts, Mongo history and local environment parsing. [E13–E14]'),
 ('OpenAI Python SDK / model','openai 3.35.1; model environment-selected','Optional Responses API integration. No verified live model or provider success. [E14]'),
 ('Node / integration Express','Node 24.x engine; Express 5.3.0 exact','Independent fictional provider. A test run under installed Node 25.8.0 is identified separately in section 7. [E16]'),
 ('MySQL / MongoDB containers','mysql:8.4; mongo:7','Compose tags; image digests not pinned. MySQL app integration partial. [E19]'),
 ('Git / Windows PowerShell / Bruno','Git revision on cover; tool versions not asserted','Version control, documented local operations and manual API collections. No CI/CD or AWS runtime established. [E01, E20–E23]')],[2700,2400,4820])
note('Source pins are reported as found, including versions newer than historical project references. This analysis does not need external documentation to establish what the repository declares; dependency safety/compatibility audits remain future work.')

page('6.1 Engineering decisions and tradeoffs')
table(['Decision observed in code','Benefit','Cost / limitation'],[
 ('Core API as identity/finance gateway','One owner-resolution boundary and authoritative aggregation before AI calls.','Core availability and relational reads affect most user workflows; gateway needs scaling and observability.'),
 ('One Java deployable with domain packages','Shared transactions, locks, foreign keys and consistent finance rules.','Modules release together; do not claim independent module deployment.'),
 ('Separate Python AI process','Isolates Python model/rules libraries and chat storage concerns.','Adds HTTP contracts, timeouts, secrets and partial failure modes.'),
 ('Relational ledger + Mongo history','SQL constraints for money/category relationships; document-shaped conversation metadata/messages.','No cross-store referential integrity or coordinated account erasure; extra backup/operational burden.'),
 ('Decimal calculations and string internal money','Avoids intermediate binary-float conversion in AI/provider boundaries; validates amounts and ratios.','Browser API still uses JSON numbers; bounded display and integer-ID precision require explicit limits.'),
 ('Cookie JWT + CSRF and token_version','JWT is not readable by Angular; password change revokes old credentials.','Per-request user/version lookup adds DB dependency; logout alone does not revoke copied tokens.'),
 ('Synchronous RestClient calls','Straightforward request tracing; no DB transaction spans mock fetch or insight network request.','Long upstream latency occupies callers; no verified queue, circuit breaker or global retry policy.'),
 ('Rule-based FINANCE fallback','Provider outages need not eliminate educational finance explanations.','Fallback is keyword constrained and differs from model wording; GENERAL has no fallback.'),
 ('Demo provider and reviewed import','Reproducible demos without bank access; real CSV input with explicit category review.','Hard-coded demo account is not a live asset; import duplicate detection is limited without references.')],[3000,3350,3570])
p('Architecture descriptions above are interpretations of the implemented boundaries, not evidence that formal architecture decision records were approved. The repository includes no shared runtime library, event broker, cache server, distributed transaction coordinator or API gateway product beyond Spring’s application-level gateway behavior. [E02–E19]')

page('7. Security and privacy assessment',True)
table(['Area','Verified implementation','Finding / boundary'],[
 ('Password handling','BCrypt cost 12; ≥12-character new passwords and ≤72 UTF-8 bytes; no hash in UserResponse. [E04–E05]','No verified MFA, password-reset flow, email verification or login rate limiting.'),
 ('JWT and sessions','HS256; ≥32 decoded-byte signing key; issuer/audience/timestamp checks; user existence/version check. [E04]','Shared signing-key rotation and emergency invalidation runbook not implemented. Stateless HTTP session still requires DB lookup.'),
 ('Browser credentials','HttpOnly, SameSite=Lax, path=/; secure __Host- default; explicit insecure local profile; CSRF rotation/bootstrap. [E04–E05]','Local HTTP deliberately relaxes cookie transport security. Secure defaults do not demonstrate deployed TLS.'),
 ('Authorization','Server-derived owner; owner-scoped repositories, SQL constraints; internal conversation parent ownership checks. [E07–E13]','No roles/admin model. Internal key compromise would expose trust in X-Owner-Id; enforce network isolation in deployment.'),
 ('CSRF / CORS','Cookie mutations require XSRF; header-only Bearer exception outside auth lifecycle; mixed credentials rejected. [E04]','No application CORS allowlist/config found. Current UI depends on same-origin proxy; do not infer cross-origin support.'),
 ('Local console / docs','H2 console enabled only local with separate permit-all chain and CSRF disabled; Swagger public. [E03–E04]','Local profile must stay local. Core listener is not explicitly bound to loopback in application properties; firewall/network exposure needs configuration.'),
 ('Internal authentication','Distinct AI/Node keys; FastAPI hmac.compare_digest; Node timingSafeEqual over SHA-256 hashes. [E06, E14, E16]','Local internal HTTP is not encrypted; private networking/TLS and key rotation proposed.'),
 ('Secrets / logging','Ignored .env templates/process variables, redacted DTOs/config, sanitized errors, restricted diagnostic loggers. [E01, E03, E14, E16]','Ignored files are hygiene, not a secret manager or historical secret scan. No raw secret values reproduced here.'),
 ('AI and chat privacy','Minimal finance aggregate; no raw ledger/user profile in model payload; store=False and tools=[] in SDK call. [E11–E14]','Category names, monetary summaries, question/history content still go to external provider when configured. No provider-retention or legal-consent guarantee follows from store=False.')],[2150,3900,3870])

page('7.1 Reliability, errors and known limitations')
table(['Area / behavior','Evidence-backed limitation or consequence'],[
 ('Timeouts and payload bounds','AI client defaults: connect 2s/read 5s; integration 2s/3s; Python provider timeout default 3s/output 800 tokens. Conversation responses bounded to 4 MiB; insights/chat 1 MiB; mock response 256 KiB/≤100 rows. Configurable values require coordinated tuning. [E03, E06, E11–E16]'),
 ('Safe error contracts','Spring ApiError: timestamp/status/code/message/path/fieldErrors. FastAPI preserves sanitized detail; Node uses code/message. Upstream bodies are not blindly forwarded; AI timeouts map to 504 and failures generally 502/503 as defined by clients/controllers. [E06, E11–E16, E22]'),
 ('Ambiguous chat persistence','Standalone MongoDB pair insert/recency update is non-atomic. Compensation may itself fail; a response timeout can occur after a write. Frontend history reconciliation limits resends but cannot guarantee exactly-once delivery. [E12–E14, E18]'),
 ('Model consistency and missing context','Model output is length/status checked, not verified against every numeric claim. FINANCE sees one month of aggregates, not raw records, balances or other months. History may contain old amounts. Prompt controls and fallback do not guarantee factual consistency. [E11–E14]'),
 ('Health / readiness','FastAPI/Node health responses are static; lazy Mongo setup can allow healthy liveness while history is unavailable. Actuator health is exposed without details; no demonstrated end-to-end dependency readiness or alerting exists. [E03, E14, E16]'),
 ('Cleanup / data lifecycle','60-second fixed-delay cleanup exists for expired previews/old receipts, not for all chat/finance data. It only runs while core is active. No complete retention/erasure/export or backup policy found. [E13, E17]'),
 ('Import integrity / scale','Owner locks and uniqueness protect known IDs; deleted imported transactions may be reimported. Without bank references, overlapping CSV exports need manual review. CSV batch size is bounded and confirmations are synchronous. [E15–E17]'),
 ('Database and deployment','H2 dialect, JDBC map assumptions and DATEADD prevent claiming MySQL portability. Compose databases and application source are not a verified AWS deployment. No measured recovery time, throughput, availability or horizontal scale is asserted. [E03, E15, E17, E19]')],[2850,7070])
h('Prioritized findings')
p('High: production relational configuration/migrations are incomplete; network and privacy controls need an explicit deployment design. Medium: ambiguous Mongo writes and missing chat idempotency; no verified login abuse controls; QA collection validation failure. Lower but relevant: historical docs conflict with current code and UI display ranges are narrower than backend numeric storage. These are assessment priorities, not penetration-test severity scores.')

page('7.2 Testing and verification record')
p('Verification used isolated test facilities on 26 October 2026, Asia/Manila. No development ledger reset, live bank connection or live model call was performed. [E20–E21]')
test_placeholder=len(body);body.append('TEST_PLACEHOLDER')
h('Coverage found in source')
p('Core tests cover identity, cookies/CSRF/Bearer, revocation, accounts, owner isolation, finance rules, migrations, imports and upstream errors. Python tests cover contracts, rules, provider failures and conversation persistence/isolation; Node tests cover authentication, normalized fixtures and sanitized errors. [E20]')
p('Angular Vitest/jsdom tests cover session restoration, finance/dashboard, bank/imports, assistant/history and account settings. The 272-test suite and build were rerun here. Browser workflows in docs/milestone-8g2-account-settings.md are prior results, not new browser execution claims. [E20–E21, E23]')
h('Observed QA issue and interpretation')
p('The environment-URL test in qa/test_bruno_collections.py failed on core-api/bruno/Untitled 1.bru because of a vars:pre-request baseUrl override. This establishes a collection-policy violation, not proof of leaked credentials. Local values were neither reproduced nor executed. [E21]')
h('Remaining verification work')
p('Live MongoDB, multi-process E2E and browser workflows were not enabled. MySQL tests, real-model evaluation, deployed HTTPS/proxy/cookie checks, restore exercises, load tests and independent security review remain release work. Mocked tests do not establish production acceptance.')

page('8. Prioritized enhancement roadmap',True)
p('All items below are proposals unless the “baseline” explicitly names an implemented piece. Priority P0 means release gate, P1 means next development cycle, and P2 means later capability work. Effort S/M/L is relative complexity, not a delivery estimate.')
table(['Priority / proposal','Value / effort','Dependencies and acceptance outcome'],[
 ('P0 · MySQL readiness','Durable target relational environment. L. Baseline: Compose container only.','Add JDBC connector/Flyway support and explicit environment profile; port schema/application SQL safely; run V1–V7 upgrade and ownership/calculation/import tests on MySQL; plan H2 data transfer and rollback.'),
 ('P0 · Secure deployment configuration','Operationally reviewable release. L. Baseline: local startup and secure cookie defaults.','Choose hosting topology, domain/TLS, same-origin /api reverse proxy, private AI/Node/database networking, least-privilege DB users and managed secrets. Verify cookies/CSRF/restoration in the deployed topology.'),
 ('P0 · Correct failing QA / documentation drift','Trustworthy handover and repeatable verification. S.','Review/remove the stray Bruno override without exposing values; rerun collection checks. Align root/service prose with V1–V7, cookie auth, imports and implemented AI mapping.'),
 ('P0 · Backups and recovery','Recover finance/chat state after failure. M. Baseline: volumes, no restore proof.','Choose RPO/RTO, encrypted storage and access policy; automate consistent SQL/Mongo backups; exercise restore into isolated infrastructure and validate ownership/record counts.'),
 ('P1 · CI/CD and artifact provenance','Prevent regressions and standardize deployment. M.','Automate Maven verify, Python/Node tests, Angular tests/build, Bruno validation and opt-in integration stages; dependency scanning; build versioned artifacts; staged deployment with rollback gates.'),
 ('P1 · AI grounding and consistency','More reliable explanations of available records. M–L.','Define representative FINANCE/GENERAL question sets, multilingual behavior, numeric-claim checks, prompt versioning, fallback transparency and missing-data rules. Track model changes without exposing private prompts in logs.'),
 ('P1 · Chat persistence / idempotency','Recover safely from retries/timeouts. M–L.','Add client-generated idempotency/turn IDs and unique indexes; consider replica-set transactions or a single atomic turn document; test outages/concurrent sends and reconcile ambiguous responses.')],[2800,2800,4320])

page('8.1 Roadmap continuation and AWS proposal')
table(['Priority / proposal','Value / effort','Dependencies and acceptance outcome'],[
 ('P1 · Observability and readiness','Diagnose service failures. M.','Add structured redacted logs, correlation IDs, metrics, latency/error dashboards, dependency readiness probes and alerts. Bound model calls/costs; avoid logging financial bodies or secrets.'),
 ('P1 · Security and privacy hardening','Reduce abuse and clarify data use. M–L.','Login rate limits, recovery/email verification design, CSP/header review, managed-secret rotation, dependency audit, explicit AI data notice/consent decision, retention/erasure/export policy and access auditing.'),
 ('P1 · Import and reconciliation','Improve statement reliability. M. Baseline: reviewed CSV import.','Add stable account identities, source adapters and overlapping-export review; define corrections/reversals, reconciliation totals and audit trail. Test partial imports and preserve existing durable external IDs.'),
 ('P2 · Real bank integrations with consent','Reduce manual ingestion. L; external dependency.','Select a provider and lawful access model; explicit consent/scopes, revocation and data minimization; secure token storage; sandbox contract tests and approved callback/webhook/polling design. No bank partnership is assumed.'),
 ('P2 · Expense analytics','Improve interpretation of recorded activity. M. Baseline: monthly dashboard and six-month UI series.','Specify trends/cohorts/recurring-cost detection, completeness indicators and explainable category rules. Reuse validated aggregates before introducing cached summaries.'),
 ('P2 · Scalability and performance','Handle measured demand. M–L.','Benchmark first; tune SQL/Mongo indexes and query counts, model concurrency and frontend multi-month requests. Reassess owner locks, scheduler coordination and chat pagination before adding caches/queues.')],[2850,2650,4420])
h('Proposed AWS hosting option — not implemented')
p('One viable target is an HTTPS application entry with a same-origin /api route, separately hosted Spring and FastAPI containers, and a private mock/real-adapter process only when needed. RDS MySQL could host the relational ledger after portability work; a supported managed MongoDB deployment could host history. Secrets Manager, CloudWatch and private network rules could supply configuration and operations. These are options for design review, not discovered AWS resources.')
p('Choose static Angular hosting only if the public rendering/build output and routing strategy support it; otherwise deploy the existing Express host and add an explicit API proxy. Do not assume Amazon DocumentDB compatibility, cost estimates, autoscaling or a regional availability design without evaluation. Hosting decisions depend on budget, team operations capability, privacy requirements and expected load.')

page('9. Conclusion and handover priorities',True)
p('YamanTracker has a coherent implemented application core: authenticated, owner-scoped finance CRUD; server-derived monthly summaries; budget utilization; profile/password management; reviewed CSV import; simulated provider synchronization; and persistent AI conversations with contextual FINANCE mode. Its separation of financial authority in Spring and AI/history responsibilities in Python is clear in the code. [E02–E18]')
p('The strongest verified choices are decimal financial calculations, relational constraints and owner checks, bounded internal contracts, HttpOnly cookie/CSRF handling and database-backed token revocation after password change. The project also has broad unit/integration test sources and isolated QA harnesses. These support maintainability and review, while remaining narrower evidence than a production security or availability certification. [E04–E14, E17, E20–E21]')
p('The main readiness gaps are the unwired MySQL target, absent verified cloud/CI deployment, undefined operational privacy/recovery policies, ambiguous non-atomic Mongo turns and an observed collection QA failure. Real bank integration remains a planned capability; the hard-coded demo balance and disabled provider cards must remain labeled as simulation/placeholders. [E13, E15–E17, E19–E23]')
h('Suggested near-term handover sequence')
steps(['Accept the working-tree baseline and capture an approved requirements/scope statement; resolve conflicts between historic reports and code.', 'Review the Bruno override and establish a reproducible all-green local verification baseline without live credentials.', 'Port and test relational migrations/application SQL on MySQL, including an upgrade rehearsal that preserves existing records.', 'Select a hosting topology and implement TLS/proxy, secrets, backup/restore and operational readiness checks.', 'Add chat idempotency/recovery and AI numeric-grounding evaluations before expanding the assistant or adding bank connectivity.'])
h('Use in defense and future development')
p('Present the demonstrated workflows as implemented application capabilities, show the diagram’s actual trust boundaries, and identify proposed cloud/bank features explicitly. For maintenance, use the API inventory with source symbols and migration references rather than treating milestone prose as a complete contract. Stakeholder decisions and glossary follow in the appendix.')

page('Appendix A. Repository evidence register',True)
p('Braced names enumerate actual files in the stated directory. Java package paths start at core-api/src/main/java/core_api/; Angular app paths start at frontend/personal-finance-ui/src/app/. In grouped entries, Python app/ paths are relative to ai-service/, Node src/ paths to integration-service/, and frontend src/ paths to frontend/personal-finance-ui/. Other paths start at the repository root. Evidence refers to the inspected working tree on the cover date.')
evidence1=[
 ('E01','README.md; .env.example; .gitignore','Local startup, service boundaries, environment-variable names and ignored local-secret policy. Historical claims are checked against source.'),
 ('E02','frontend/personal-finance-ui/{package.json,angular.json,proxy.conf.json}; src/server.ts; src/app/{app.routes.ts,app.routes.server.ts,app.config.ts}','Angular/runtime/build declarations, lazy guarded routes, client/private rendering, XSRF configuration and dev proxy; optional Express host.'),
 ('E03','core-api/pom.xml; core-api/.mvn/wrapper/maven-wrapper.properties; core-api/src/main/resources/application{,-local}.properties; core-api/src/test/resources/application-test.properties','Java/Spring/build versions; H2 local/test, validation/migration settings, multipart limit, internal timeouts and no explicit MySQL driver/profile.'),
 ('E04','security/{SecurityConfiguration,BrowserCredentials,JwtConfiguration,TokenService,LocalConsoleSecurityConfiguration}.java','BCrypt(12), cookie/header policy, CSRF matcher, claim/version validation, 60-minute HS256 issue, local-console exception.'),
 ('E05','auth/{AuthController,AuthService,RegisterRequest,LoginRequest,BcryptPasswordValidator}.java; user/{User,UserController,UserService,UserRepository,UserResponse,ChangePasswordRequest,UpdateProfileRequest}.java','Registration/login/session/logout, name/password updates, account locks and token_version increment; no browser token in login body.'),
 ('E06','insights/{AiClientConfiguration,AiServiceProperties,FinancialInsightsClient}.java; integration/{IntegrationClientConfiguration,IntegrationProperties,MockTransactionClient}.java','Dedicated internal HTTP clients, keys, timeouts, body bounds and sanitized upstream status handling.'),
 ('E07','category/{CategoryController,CategoryService,CategoryRepository,CategoryRequest,CategoryDefaultInitialization}.java','Category CRUD, one-time defaults, normalized names, owner locks and referenced-category checks.'),
 ('E08','transaction/{TransactionController,TransactionService,TransactionRepository,TransactionSpecifications,TransactionQuery,TransactionRequest,TransactionResponse,TransactionPageResponse,TransactionSource}.java','Finance CRUD/filter/page contract; owner/category/type checks; source/external IDs and per-row mock import transaction.')]
for eid,paths,symbols in evidence1:
    p(eid,'Eyebrow');note(paths);p(symbols)

page('Appendix A. Evidence register — continued')
evidence2=[
 ('E09','budget/{BudgetController,BudgetService,BudgetRequest,BudgetPeriod,BudgetUtilization,BudgetResponse}.java; dashboard/FinancialSummary.java','Monthly expense budgets, aggregate spending, exact status comparisons, savings/rate calculation.'),
 ('E10','dashboard/{DashboardController,DashboardService,DashboardResponse}.java; Angular features/dashboard/{dashboard.store,dashboard.mapper}.ts; core/services/{dashboard,finance-changes}.ts','Server aggregates, zero-filled daily series, five recent/top records and six-month UI queries/range guards.'),
 ('E11','chat/{ChatController,ChatService,ChatMessageRequest,ChatResponse}.java; insights/{InsightsController,InsightsService,AiFinancialRequest,FinancialInsightsClient}.java','Stateless AI/insights APIs, server-derived minimal context and decimal-string mapping.'),
 ('E12','chat/{ConversationController,ConversationService,ConversationClient,ConversationPayload,ConversationRequest,ConversationMessageRequest}.java','Owned conversation gateway, trusted headers, metadata-first finance context and paginated endpoints.'),
 ('E13','ai-service/app/api/conversations.py; app/services/conversation_repository.py; app/schemas/{chat,conversations}.py; app/core/mongodb.py','trusted_owner; create/listing/read/context/save_turn; ownership/indexes, last 12 messages, compensation and Mongo timeouts.'),
 ('E14','ai-service/requirements{,-dev}.txt; pyproject.toml; app/{main.py,core/config.py,api/insights.py,api/chat.py,schemas/financial.py,services/chatbot_service.py,services/openai_chat_provider.py,services/insight_engine.py}','Settings, require_internal_key, strict summary validation, analyze, rule_based_answer, model_input and Responses SDK orchestration.'),
 ('E15','bank/{BankController,BankService,StatementCsv}.java; Angular core/services/bank.ts; features/bank-connections/bank-connections.{ts,html}','Demo persistence, hard-coded simulated balance, disabled provider buttons, CSV columns/preview/confirm/cancel and deduplication.'),
 ('E16','integration-service/package.json; src/{app,server,config}.js; src/routes/mock-transactions.js; src/middleware/internal-auth.js; src/services/{mock-provider,normalizer}.js; Java integration/MockImportService.java','Node executable provider, loopback, fixture normalization/auth; Spring importFor counts and row-level transactions.')]
for eid,paths,symbols in evidence2:
    p(eid,'Eyebrow');note(paths);p(symbols)

page('Appendix A. Evidence register — validation and gaps')
for eid,paths,symbols in [
 ('E17','core-api/src/main/resources/db/migration/V1__create_users.sql through V7__user_token_version.sql; bank/StatementCleanup.java; BankService.purgeExpired','SQL ownership/uniqueness/check constraints, all seven migration steps, @Scheduled(fixedDelay=60000), 30-minute preview / receipt pruning.'),
 ('E18','Angular core/services/{auth,ai-chat}.ts; core/interceptors/auth.interceptor.ts; core/guards/auth.guard.ts; features/account-settings/account-settings.ts; shared/components/ai-assistant/ai-assistant.ts','Safe profile state, CSRF bootstrap, restoration, session revisions/BroadcastChannel, paginated history and uncertain-send reconciliation.'),
 ('E19','docker-compose.yml; core-api/pom.xml; application.properties and application-local.properties','mysql:8.4/mongo:7 database infrastructure only, credentials, loopback ports, healthchecks/volumes; absent app MySQL connection dependency/profile.'),
 ('E20','core-api/src/test/java/core_api/; ai-service/tests/; integration-service/tests/service.test.js; frontend/personal-finance-ui/src/app/**/*.spec.ts','Source test inventory for security, accounts, finance, migrations, upstream clients, AI contracts, persistence and UI behavior. Current run details in section 7.2.'),
 ('E21','qa/{test_backend_e2e,test_cookie_browser,test_bruno_collections}.py; qa/check-bruno.mjs; frontend/personal-finance-ui/qa/; core-api/bruno/Untitled 1.bru','Opt-in isolated E2E/browser harness; static collection policy and observed vars:pre-request URL-override failure.'),
 ('E22','error/{ApiError,ApiExceptionHandler,SecurityErrorHandler}.java; FastAPI main.py; Node src/app.js','Safe public error fields, validation/error filtering and sanitized upstream exception boundaries.'),
 ('E23','docs/{milestone-9,milestone-7d,milestone-8e1-authentication,milestone-8g1-account-management,milestone-8g2-account-settings,MILESTONE_8C_6_BANK_CONNECTIONS}.md; service READMEs; .github/modernize/','Historical milestone context and archived test reports; .github contains modernization hooks rather than a verified build/deploy workflow.')]:
    p(eid,'Eyebrow');note(paths);p(symbols)
h('Verified discrepancies from investigative leads and older prose')
p('YamanTracker and Ask Yaman Ai are current UI labels; FinanceAI/FinanceAi persists in older README headings and technical names. The code does not establish a complete historical rename record. MySQL is infrastructure-only; H2 is active locally. Real Connect buttons remain disabled, but demo sync and CSV import exist. Root startup prose mentions V1–V5; source includes V1–V7. Older AI setup prose calls Spring mapping future work, whereas AiFinancialRequest and clients implement it. [E01–E03, E11, E15, E17, E23]')

page('Appendix B. Glossary and stakeholder confirmations')
table(['Term','Meaning in this system'],[
 ('Core API / module','Spring application / internal Java package. A package does not run independently.'),
 ('Deployable service','A separately executable process: core-api, ai-service or integration-service; frontend is a separate application deployment unit.'),
 ('JWT / token_version','Signed access credential / stored user counter that invalidates previously issued tokens after password change.'),
 ('CSRF / XSRF','Protection against forged cookie-authenticated mutations; readable XSRF cookie is echoed through X-XSRF-TOKEN.'),
 ('Financial aggregate','Selected-month totals/category/budget values derived by Spring; not a raw transaction list or verified bank statement balance.'),
 ('FINANCE / GENERAL','Chat modes with current monthly summary / without personalized financial context. GENERAL needs a configured provider.'),
 ('Rule-based fallback','Deterministic educational explanation used when the FINANCE provider path fails or is absent.'),
 ('Flyway / migration','Versioned schema change mechanism; immutable applied files preserve upgrade checksums.'),
 ('Idempotency / compensation','Repeat-safe effect / best-effort undo after partial failure. Chat has compensation but no idempotency-key protocol.'),
 ('Liveness / readiness','Process can respond / process can meet required dependencies and serve the intended workflow.'),
 ('RPO / RTO','Proposed recovery-point (acceptable data loss) / recovery-time objectives; not established targets in the repository.')],[2550,7370])
h('Items requiring stakeholder confirmation')
steps(['Confirm primary user population, PHP-only currency policy, scope of GENERAL chat and educational-use positioning.', 'Approve MySQL target/version, H2 data transfer requirements and whether existing local records must be retained.', 'Choose AWS or another deployment environment, operational owner, budget and expected load; define availability and recovery objectives.', 'Decide external-model data notice/consent, history/finance retention, account deletion/export, and permitted provider/model configuration.', 'Confirm bank provider feasibility and consent requirements before promising live integration; retain clear simulated/demo labels.', 'Review whether the current working-tree edits are the approved defense/handover baseline, and resolve the identified QA/documentation discrepancies.'])
note('Document end. All roadmap items remain proposals until separately implemented and validated.')

def build():
    results_path=OUT/'verification-results.json'
    result_rows=json.loads(results_path.read_text(encoding='utf-8')) if results_path.exists() else [
        ['Python AI suite','164 passed; 1 skipped','pytest in ai-service; real MongoDB test opt-in.'],
        ['Node integration suite','39 passed','node --test --test-isolation=none; observed runtime v25.8.0 (declared engine 24.x).'],
        ['QA collection suite','15 passed; 1 failed; 6 skipped','Failure: core-api/bruno/Untitled 1.bru overrides baseUrl in vars:pre-request. Live E2E/browser not enabled.'],
        ['Bruno JavaScript syntax','36 embedded blocks validated','node qa/check-bruno.mjs; syntax only, no requests executed.'],
        ['Core API Maven verify','See final verification update','Offline Maven verification in progress at drafting time.'],
        ['Frontend / live E2E','Source reviewed; not rerun','Prior milestone report distinguished from this pass.']]
    old=len(body);table(['Verification','Current result','Scope / limitation'],result_rows,[2300,2250,5370]);test_xml=''.join(body[old:]);del body[old:]
    body[test_placeholder]=test_xml
    chapters=[(3,'1. Executive summary and problem statement'),(4,'2. System overview and scope'),(6,'3. High-level architecture'),(9,'4. End-to-end system flows'),(16,'5. API and data design'),(22,'6. Technology stack and decisions'),(24,'7. Security, reliability and testing'),(27,'8. Future enhancements and roadmap'),(29,'9. Conclusion and handover priorities'),(30,'Appendix A. Repository evidence register'),(33,'Appendix B. Glossary and confirmations')]
    toc='<w:sdt><w:sdtContent>'
    for num,label in chapters:
        toc+=f'<w:p><w:pPr><w:pStyle w:val="TOC1"/><w:tabs><w:tab w:val="right" w:leader="dot" w:pos="9850"/></w:tabs></w:pPr><w:hyperlink w:anchor="page{num}">'+text_run(label)+'<w:r><w:tab/></w:r></w:hyperlink><w:fldSimple w:instr=" PAGEREF page'+str(num)+' \\h "><w:r><w:t>'+str(num)+'</w:t></w:r></w:fldSimple></w:p>'
    body[toc_index]=toc+'</w:sdtContent></w:sdt>'
    # Page references above are cached designed-page values; Word can update fields.
    sect='<w:sectPr><w:headerReference w:type="default" r:id="rIdHeader"/><w:footerReference w:type="default" r:id="rIdFooter"/><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1000" w:right="993" w:bottom="1000" w:left="993" w:header="400" w:footer="400"/><w:titlePg/></w:sectPr>'
    doc='<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="'+W+'" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>'+''.join(body)+sect+'</w:body></w:document>'
    styles=f'''<?xml version="1.0" encoding="UTF-8"?><w:styles xmlns:w="{W}">
    <w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/><w:sz w:val="21"/><w:color w:val="243B46"/><w:lang w:val="en-US"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="110" w:line="265" w:lineRule="auto"/><w:widowControl/></w:pPr></w:pPrDefault></w:docDefaults>
    <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
    <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:pPr><w:keepNext/><w:spacing w:before="40" w:after="220"/><w:outlineLvl w:val="0"/></w:pPr><w:rPr><w:b/><w:color w:val="173C4A"/><w:sz w:val="37"/></w:rPr></w:style>
    <w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:pPr><w:keepNext/><w:spacing w:before="120" w:after="130"/><w:outlineLvl w:val="1"/></w:pPr><w:rPr><w:b/><w:color w:val="1D5964"/><w:sz w:val="27"/></w:rPr></w:style>
    <w:style w:type="paragraph" w:styleId="CoverTitle"><w:name w:val="Cover Title"/><w:pPr><w:spacing w:before="300" w:after="300"/></w:pPr><w:rPr><w:b/><w:sz w:val="61"/><w:color w:val="173C4A"/></w:rPr></w:style>
    <w:style w:type="paragraph" w:styleId="Subtitle"><w:name w:val="Subtitle"/><w:pPr><w:spacing w:after="220"/></w:pPr><w:rPr><w:sz w:val="25"/><w:color w:val="52727C"/></w:rPr></w:style>
    <w:style w:type="paragraph" w:styleId="Eyebrow"><w:name w:val="Eyebrow"/><w:pPr><w:spacing w:after="120"/></w:pPr><w:rPr><w:sz w:val="17"/><w:b/><w:color w:val="A06F1B"/></w:rPr></w:style>
    <w:style w:type="paragraph" w:styleId="Evidence"><w:name w:val="Evidence"/><w:pPr><w:spacing w:after="90" w:line="240" w:lineRule="auto"/></w:pPr><w:rPr><w:sz w:val="18"/><w:color w:val="526A76"/></w:rPr></w:style>
    <w:style w:type="paragraph" w:styleId="Caption"><w:name w:val="Caption"/><w:pPr><w:spacing w:after="170"/></w:pPr><w:rPr><w:i/><w:sz w:val="18"/><w:color w:val="526A76"/></w:rPr></w:style>
    <w:style w:type="paragraph" w:styleId="TableText"><w:name w:val="Table Text"/><w:pPr><w:spacing w:after="0" w:line="230" w:lineRule="auto"/></w:pPr><w:rPr><w:sz w:val="19"/></w:rPr></w:style>
    <w:style w:type="paragraph" w:styleId="Spacer"><w:name w:val="Spacer"/><w:pPr><w:spacing w:after="30" w:line="60" w:lineRule="exact"/></w:pPr><w:rPr><w:sz w:val="4"/></w:rPr></w:style>
    <w:style w:type="paragraph" w:styleId="TOC1"><w:name w:val="toc 1"/><w:pPr><w:spacing w:after="160"/></w:pPr><w:rPr><w:sz w:val="22"/></w:rPr></w:style>
    <w:style w:type="table" w:styleId="EvidenceTable"><w:name w:val="Evidence Table"/><w:tblPr><w:tblBorders><w:bottom w:val="single" w:sz="4" w:color="D2E1E5"/><w:insideH w:val="single" w:sz="4" w:color="D2E1E5"/></w:tblBorders></w:tblPr></w:style>
    </w:styles>'''
    relationships='<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'+''.join(rels)+''.join(f'<Relationship Id="rId{k}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/{typ}" Target="{target}"/>' for k,typ,target in [('Styles','styles','styles.xml'),('Settings','settings','settings.xml'),('Header','header','header1.xml'),('Footer','footer','footer1.xml')])+'</Relationships>'
    ctypes='<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/>'+''.join(f'<Override PartName="/{name}" ContentType="{ctype}"/>' for name,ctype in [('word/document.xml','application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml'),('word/styles.xml','application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml'),('word/settings.xml','application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml'),('word/header1.xml','application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml'),('word/footer1.xml','application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml'),('docProps/core.xml','application/vnd.openxmlformats-package.core-properties+xml')])+'</Types>'
    target=OUT/'YamanTracker_System_Analysis_and_Technical_Architecture.docx'
    with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
        for name,content in {
            '[Content_Types].xml':ctypes,
            '_rels/.rels':'<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/></Relationships>',
            'word/document.xml':doc,'word/styles.xml':styles,'word/_rels/document.xml.rels':relationships,
            'word/settings.xml':f'<w:settings xmlns:w="{W}"><w:zoom w:percent="100"/><w:defaultTabStop w:val="720"/><w:updateFields w:val="true"/></w:settings>',
            'word/header1.xml':f'<w:hdr xmlns:w="{W}"><w:p><w:pPr><w:pBdr><w:bottom w:val="single" w:sz="5" w:color="D2E1E5"/></w:pBdr></w:pPr><w:r><w:rPr><w:sz w:val="17"/><w:color w:val="52727C"/></w:rPr><w:t>YAMANTRACKER  |  SYSTEM ANALYSIS AND TECHNICAL ARCHITECTURE</w:t></w:r></w:p></w:hdr>',
            'word/footer1.xml':f'<w:ftr xmlns:w="{W}"><w:p><w:pPr><w:tabs><w:tab w:val="right" w:pos="9920"/></w:tabs></w:pPr><w:r><w:rPr><w:sz w:val="17"/><w:color w:val="52727C"/></w:rPr><w:t>Source-verified baseline · 26 October 2026</w:t><w:tab/></w:r><w:fldSimple w:instr="PAGE"><w:r><w:t>1</w:t></w:r></w:fldSimple></w:p></w:ftr>',
            'docProps/core.xml':'<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>'+escape(TITLE)+'</dc:title><dc:subject>Capstone defense and technical handover</dc:subject><dc:creator>YamanTracker Technical Analysis</dc:creator><dc:description>Repository-backed architecture, API/data design, security findings, validation and proposed roadmap.</dc:description></cp:coreProperties>'
        }.items():
            ET.fromstring(content);z.writestr(name,content.encode('utf-8'))
        for file in images:z.write(file,'word/media/'+file.name)
    with zipfile.ZipFile(target) as z:
        assert z.testzip() is None
        for name in z.namelist():
            if name.endswith('.xml') or name.endswith('.rels'): ET.fromstring(z.read(name))
    plain=' '.join(ET.fromstring(doc).itertext())
    assert 'PLACEHOLDER' not in plain
    assert len(pages)==33, len(pages)
    print(json.dumps({'document':str(target),'designed_pages':len(pages),'words':len(plain.split()),'embedded_diagrams':len(images),'bytes':target.stat().st_size}))
    (OUT/'page-plan.json').write_text(json.dumps({i+1:t for i,t in enumerate(pages)},indent=2),encoding='utf-8')

if __name__=='__main__':build()
