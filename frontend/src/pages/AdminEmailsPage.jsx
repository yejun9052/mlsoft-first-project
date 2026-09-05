import { useEffect, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import PageHeader from '../components/ui/PageHeader.jsx';
import Tabs from '../components/ui/Tabs.jsx';
import Card from '../components/ui/Card.jsx';
import TableCard from '../components/ui/TableCard.jsx';
import Table, { THead, Th, TR, Td } from '../components/ui/Table.jsx';
import Button from '../components/ui/Button.jsx';
import Field from '../components/ui/Field.jsx';
import TextInput from '../components/ui/TextInput.jsx';
import Textarea from '../components/ui/Textarea.jsx';
import Chip from '../components/ui/Chip.jsx';
import FilterGroup from '../components/ui/FilterGroup.jsx';
import StatusBadge from '../components/ui/StatusBadge.jsx';
import Pagination from '../components/ui/Pagination.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import { usePageClamp } from '../hooks/usePageClamp.js';
import { EMAIL_STATUS_LABEL, EMAIL_STATUS_TONE, EMAIL_TYPE_LABEL } from '../constants/status.js';
import {
  useEmailHistories,
  useReminderTargets,
  useResendEmail,
  useSendBulkEmail,
} from '../hooks/useEmails.js';
import {
  useEmailTemplates,
  usePreviewEmailTemplate,
  useUpdateEmailTemplate,
} from '../hooks/useEmailTemplates.js';

const TAB_TARGETS = 'targets';
const TAB_TEMPLATE = 'template';
const TAB_HISTORY = 'history';

// 회당 발송 상한 — 서버와 같은 값이다. 화면에서 먼저 막는 것은 거절될 요청을 보내지 않기 위해서고,
// **판정의 원본은 서버다**. 일 400건 한도는 당일 누적이라 화면이 알 수 없어 서버 거부에 맡긴다.
const MAX_BULK_RECIPIENTS = 100;

const PAGE_SIZE = 10;

const TYPE_FILTERS = ['ALL', 'LEAVE', 'WELFARE', 'REMINDER', 'NOTICE'];
const STATUS_FILTERS = ['ALL', 'PENDING', 'SENDING', 'SENT', 'FAILED'];

// 이메일 관리 — 소진 안내 대상 조회·일괄 발송 / 양식 편집 / 발송 이력 (docs/01 2-8).
// 세 탭을 한 화면에 둔 것은 순서가 그대로 작업 흐름이기 때문이다:
// 누구에게 보낼지 고르고 → 무슨 문구로 보낼지 정하고 → 나갔는지 확인한다.
export default function AdminEmailsPage() {
  const [tab, setTab] = useState(TAB_TARGETS);

  const tabItems = [
    { value: TAB_TARGETS, label: '대상·일괄 발송' },
    { value: TAB_TEMPLATE, label: '양식' },
    { value: TAB_HISTORY, label: '발송 이력' },
  ];

  return (
    <div>
      <PageHeader
        title="이메일 관리"
        subtitle="연차 소진 안내 대상 조회·일괄 발송, 메일 양식, 발송 이력"
      />

      <Tabs tabs={tabItems} value={tab} onChange={setTab} className="mb-5" />

      {tab === TAB_TARGETS && <TargetsTab />}
      {tab === TAB_TEMPLATE && <TemplateTab />}
      {tab === TAB_HISTORY && <HistoryTab />}
    </div>
  );
}

// ── 1. 대상·일괄 발송 ───────────────────────────────────────────────────
function TargetsTab() {
  const targetsQuery = useReminderTargets();
  const sendMutation = useSendBulkEmail();

  // 선택은 userId 배열로 들고 있는다 — 요청 본문이 그대로 배열이고 상한 판정도 길이 하나로 끝난다
  const [selectedIds, setSelectedIds] = useState([]);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [result, setResult] = useState(null);

  const targets = targetsQuery.data ?? [];
  // 메일 주소가 없는 사원은 고를 수 없다 — 골라 봐야 서버가 skipped로 떨어뜨린다
  const selectableIds = targets.filter((target) => target.emailAvailable).map((t) => t.userId);

  // 대상 목록이 바뀌면(재조회·발송 후) 사라진 사원의 선택은 버린다.
  // 남겨 두면 화면에 없는 사람에게 발송 요청이 나간다.
  useEffect(() => {
    const available = (targetsQuery.data ?? [])
      .filter((target) => target.emailAvailable)
      .map((target) => target.userId);
    setSelectedIds((prev) => prev.filter((id) => available.includes(id)));
  }, [targetsQuery.data]);

  const allSelected = selectableIds.length > 0 && selectedIds.length === selectableIds.length;
  const overLimit = selectedIds.length > MAX_BULK_RECIPIENTS;
  const canSend =
    selectedIds.length > 0 && !overLimit && title.trim() !== '' && content.trim() !== '';

  function toggleOne(userId) {
    setSelectedIds((prev) =>
      prev.includes(userId) ? prev.filter((id) => id !== userId) : [...prev, userId],
    );
  }

  function toggleAll() {
    setSelectedIds(allSelected ? [] : selectableIds);
  }

  function send() {
    sendMutation.mutate(
      { userIds: selectedIds, title: title.trim(), content: content.trim() },
      {
        onSuccess: (data) => {
          setResult(data);
          setSelectedIds([]);
          toast.success(`${data.queued}건을 발송 대기열에 넣었습니다.`);
        },
      },
    );
  }

  return (
    <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_360px]">
      <TableCard
        title="연차 소진 안내 대상"
        right={
          <span className="text-[11px] text-ink-faint tabular-nums">
            선택 {selectedIds.length} / {selectableIds.length}명
          </span>
        }
        loading={targetsQuery.isLoading}
        error={targetsQuery.isError}
        errorLabel="대상자를 불러오지 못했습니다."
        onRetry={targetsQuery.refetch}
        empty={!targetsQuery.isLoading && targets.length === 0}
        emptyLabel="기산일이 임박한 대상자가 없습니다."
      >
        <Table className="min-w-[720px]">
          <THead>
            <Th className="w-10">
              <input
                type="checkbox"
                aria-label="전체 선택"
                checked={allSelected}
                disabled={selectableIds.length === 0}
                onChange={toggleAll}
              />
            </Th>
            <Th>이름</Th>
            <Th>부서</Th>
            <Th right>잔여</Th>
            <Th>다음 기산일</Th>
            <Th right>남은 일수</Th>
            <Th>발송 가능</Th>
          </THead>
          <tbody>
            {targets.map((target) => (
              <TR key={target.userId}>
                <Td>
                  <input
                    type="checkbox"
                    aria-label={`${target.name} 선택`}
                    checked={selectedIds.includes(target.userId)}
                    disabled={!target.emailAvailable}
                    onChange={() => toggleOne(target.userId)}
                  />
                </Td>
                <Td className="font-medium text-ink-hi">{target.name}</Td>
                <Td className="text-ink-body">{target.departmentName ?? '미배정'}</Td>
                <Td right className="text-ink-body tabular-nums">
                  {Number(target.remainingDays)}일
                </Td>
                <Td className="text-ink-mute tabular-nums">{target.nextResetDate}</Td>
                <Td right className="text-ink-body tabular-nums">
                  D-{target.daysUntilReset}
                </Td>
                <Td>
                  {target.emailAvailable ? (
                    <StatusBadge label="가능" tone="ok" />
                  ) : (
                    <StatusBadge label="메일 없음" tone="muted" />
                  )}
                </Td>
              </TR>
            ))}
          </tbody>
        </Table>
      </TableCard>

      <Card title="선택 발송">
        <Field label="제목" required>
          <TextInput
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            placeholder="예: 연차 소진 안내"
          />
        </Field>

        <Field label="본문" required className="mt-4">
          <Textarea
            rows={8}
            value={content}
            onChange={(event) => setContent(event.target.value)}
            placeholder="수신자에게 보낼 안내 문구를 적어주세요."
          />
        </Field>

        {/* 상한을 넘었을 때 버튼만 죽이면 왜 못 누르는지 알 수 없다 */}
        {overLimit && (
          <p className="mt-3 text-[12px] text-danger">
            한 번에 최대 {MAX_BULK_RECIPIENTS}명까지 보낼 수 있습니다. 현재 {selectedIds.length}명이
            선택돼 있습니다.
          </p>
        )}

        <Button
          className="mt-4 w-full"
          disabled={!canSend}
          loading={sendMutation.isPending}
          onClick={send}
        >
          선택 발송
        </Button>

        {result && (
          <dl className="mt-4 grid grid-cols-3 gap-2 text-center">
            <ResultCell label="요청" value={result.requested} />
            <ResultCell label="큐 적재" value={result.queued} />
            <ResultCell label="건너뜀" value={result.skipped} />
          </dl>
        )}
      </Card>
    </div>
  );
}

function ResultCell({ label, value }) {
  return (
    <div className="rounded-btn border border-white/[0.12] bg-white/[0.03] px-2 py-2.5">
      <dt className="text-[11px] text-ink-faint">{label}</dt>
      <dd className="mt-0.5 text-[17px] font-bold text-ink-hi tabular-nums">{value}</dd>
    </div>
  );
}

// ── 2. 양식 ────────────────────────────────────────────────────────────
function TemplateTab() {
  const templatesQuery = useEmailTemplates();
  const updateMutation = useUpdateEmailTemplate();
  const previewMutation = usePreviewEmailTemplate();

  const template = templatesQuery.data?.[0];

  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [preview, setPreview] = useState(null);

  const subjectRef = useRef(null);
  const bodyRef = useRef(null);
  // 변수 칩이 어느 입력으로 들어갈지 — 마지막으로 포커스한 쪽. 기본값은 본문
  const [focusedField, setFocusedField] = useState('body');
  // 서버 값으로 초기화한 양식 키 — 저장 후 재조회 때 편집 중인 입력을 덮어쓰지 않게 한다
  const loadedKey = useRef(null);

  useEffect(() => {
    if (!template || loadedKey.current === template.templateKey) return;
    loadedKey.current = template.templateKey;
    setSubject(template.subjectTemplate);
    setBody(template.bodyTemplate);
  }, [template]);

  // 커서 위치에 변수를 끼워 넣는다 — 끝에만 붙이면 문장 중간에 넣을 때마다 잘라 옮겨야 한다
  function insertVariable(variable) {
    const isSubject = focusedField === 'subject';
    const element = isSubject ? subjectRef.current : bodyRef.current;
    const value = isSubject ? subject : body;
    const setValue = isSubject ? setSubject : setBody;
    const caret = element?.selectionStart ?? value.length;

    setValue(`${value.slice(0, caret)}${variable}${value.slice(caret)}`);

    // 상태 반영 뒤 커서를 삽입한 변수 뒤로 옮긴다 — 그대로 두면 칩을 연달아 누를 때 순서가 뒤집힌다
    const position = caret + variable.length;
    queueMicrotask(() => {
      if (!element) return;
      element.focus();
      element.setSelectionRange(position, position);
    });
  }

  function save() {
    updateMutation.mutate(
      { templateKey: template.templateKey, subjectTemplate: subject, bodyTemplate: body },
      { onSuccess: () => toast.success('메일 양식을 저장했습니다.') },
    );
  }

  function requestPreview() {
    previewMutation.mutate(
      { templateKey: template.templateKey, subjectTemplate: subject, bodyTemplate: body },
      { onSuccess: setPreview },
    );
  }

  if (templatesQuery.isLoading) {
    return (
      <Card padding="none">
        <LoadingState />
      </Card>
    );
  }
  if (templatesQuery.isError || !template) {
    return (
      <Card padding="none">
        <ErrorState label="메일 양식을 불러오지 못했습니다." onRetry={templatesQuery.refetch} />
      </Card>
    );
  }

  return (
    <div className="grid gap-5 lg:grid-cols-2">
      <Card
        title="연차 소진 안내 양식"
        right={
          <span className="text-[11px] text-ink-faint tabular-nums">
            v{template.version}
            {template.updatedByName ? ` · ${template.updatedByName}` : ''}
          </span>
        }
      >
        <Field label="제목" required>
          <TextInput
            ref={subjectRef}
            aria-label="양식 제목"
            value={subject}
            onChange={(event) => setSubject(event.target.value)}
            onFocus={() => setFocusedField('subject')}
          />
        </Field>

        <Field label="본문" required className="mt-4">
          <Textarea
            ref={bodyRef}
            aria-label="양식 본문"
            rows={12}
            value={body}
            onChange={(event) => setBody(event.target.value)}
            onFocus={() => setFocusedField('body')}
          />
        </Field>

        <div className="mt-4">
          <FilterGroup label="변수">
            {(template.variables ?? []).map((variable) => (
              <Chip key={variable} onClick={() => insertVariable(variable)}>
                {variable}
              </Chip>
            ))}
          </FilterGroup>
          <p className="mt-2 text-[11px] text-ink-faint">
            칩을 누르면 마지막으로 편집한 입력의 커서 위치에 들어갑니다.
          </p>
        </div>

        <div className="mt-5 flex items-center gap-2">
          <Button loading={updateMutation.isPending} onClick={save}>
            저장
          </Button>
          <Button variant="secondary" loading={previewMutation.isPending} onClick={requestPreview}>
            미리보기
          </Button>
        </div>
      </Card>

      <Card title="미리보기">
        {preview ? (
          <>
            <p className="mb-3 text-[13px] font-semibold text-ink-hi">{preview.subject}</p>
            {/* 서버가 만든 HTML이라도 이 화면의 DOM에 직접 붙이지 않는다 — 권한을 모두 뺀
                sandbox iframe 안에서만 렌더해 스크립트·폼 제출이 아예 동작하지 않게 한다 */}
            <iframe
              title="메일 미리보기"
              srcDoc={preview.html}
              sandbox=""
              className="h-[420px] w-full rounded-btn border border-white/[0.12] bg-white"
            />
          </>
        ) : (
          <p className="py-12 text-center text-[13px] text-ink-mute">
            미리보기를 누르면 샘플 값으로 치환한 결과가 표시됩니다.
          </p>
        )}
      </Card>
    </div>
  );
}

// ── 3. 발송 이력 ───────────────────────────────────────────────────────
function HistoryTab() {
  const [type, setType] = useState('ALL');
  const [status, setStatus] = useState('ALL');
  const [page, setPage] = useState(0);

  const historiesQuery = useEmailHistories({
    page,
    size: PAGE_SIZE,
    type: type === 'ALL' ? undefined : type,
    status: status === 'ALL' ? undefined : status,
  });
  const resendMutation = useResendEmail();

  const rows = historiesQuery.data?.content ?? [];
  const pageInfo = historiesQuery.data?.page;
  usePageClamp(page, setPage, pageInfo?.totalPages);

  // 필터를 바꾸면 총 페이지가 달라지므로 항상 첫 페이지로 되돌린다
  function changeType(next) {
    setType(next);
    setPage(0);
  }
  function changeStatus(next) {
    setStatus(next);
    setPage(0);
  }

  function resend(history) {
    resendMutation.mutate(history.id, {
      onSuccess: () => toast.success(`${history.recipientName}님에게 재발송을 요청했습니다.`),
    });
  }

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-end gap-3">
        <FilterGroup label="유형">
          {TYPE_FILTERS.map((value) => (
            <Chip key={value} active={type === value} onClick={() => changeType(value)}>
              {value === 'ALL' ? '전체' : EMAIL_TYPE_LABEL[value]}
            </Chip>
          ))}
        </FilterGroup>
        <FilterGroup label="상태">
          {STATUS_FILTERS.map((value) => (
            <Chip key={value} active={status === value} onClick={() => changeStatus(value)}>
              {value === 'ALL' ? '전체' : EMAIL_STATUS_LABEL[value]}
            </Chip>
          ))}
        </FilterGroup>
      </div>

      <TableCard
        loading={historiesQuery.isLoading}
        error={historiesQuery.isError}
        errorLabel="발송 이력을 불러오지 못했습니다."
        onRetry={historiesQuery.refetch}
        empty={!historiesQuery.isLoading && rows.length === 0}
        emptyLabel="발송 이력이 없습니다."
      >
        <Table className="min-w-[980px]">
          <THead>
            <Th>수신자</Th>
            <Th>유형</Th>
            <Th>상태</Th>
            <Th>제목</Th>
            <Th right>재시도</Th>
            <Th>오류</Th>
            <Th>발송 시각</Th>
            <Th>생성 시각</Th>
            <Th>{''}</Th>
          </THead>
          <tbody>
            {rows.map((history) => (
              <TR key={history.id}>
                <Td>
                  <span className="font-medium text-ink-hi">{history.recipientName}</span>
                  <span className="ml-2 text-[11px] text-ink-faint">
                    {history.recipientEmailMasked}
                  </span>
                </Td>
                <Td className="text-ink-body">{EMAIL_TYPE_LABEL[history.type] ?? history.type}</Td>
                <Td>
                  <StatusBadge
                    label={EMAIL_STATUS_LABEL[history.status] ?? history.status}
                    tone={EMAIL_STATUS_TONE[history.status]}
                  />
                </Td>
                <Td className="max-w-[220px] truncate text-ink-body" title={history.title}>
                  {history.title}
                </Td>
                <Td right className="text-ink-body tabular-nums">
                  {history.retryCount}
                </Td>
                <Td className="max-w-[200px] truncate text-ink-mute" title={history.errorMessage}>
                  {history.errorMessage || '—'}
                </Td>
                <Td className="text-ink-mute tabular-nums">{formatDateTime(history.sentAt)}</Td>
                <Td className="text-ink-mute tabular-nums">{formatDateTime(history.createdAt)}</Td>
                <Td right>
                  {/* 재발송은 FAILED에만 붙인다 — 다른 상태는 서버가 400으로 막는다 */}
                  {history.status === 'FAILED' && (
                    <Button
                      variant="secondary"
                      size="sm"
                      loading={resendMutation.isPending}
                      onClick={() => resend(history)}
                    >
                      재발송
                    </Button>
                  )}
                </Td>
              </TR>
            ))}
          </tbody>
        </Table>
        <Pagination
          page={page}
          totalPages={pageInfo?.totalPages}
          totalElements={pageInfo?.totalElements}
          onChange={setPage}
        />
      </TableCard>
    </div>
  );
}

// 'YYYY-MM-DD HH:mm' — 초는 이력을 훑는 데 필요 없어 잘라낸다 (AdminHistoryPage와 같은 규칙)
function formatDateTime(value) {
  if (!value) return '—';
  return String(value).replace('T', ' ').slice(0, 16);
}
