import { Subject } from 'rxjs';
import { TestBed } from '@angular/core/testing';
import { DocumentNotificationService } from './document-notification.service';
import { NotificationSessionState } from './notification-session-state.service';
import { DocumentNotificationEvent, NotificationConnectionState } from './notification-event.models';
import { notificationEventFixtures } from './testing/notification-event.fixtures';

describe('NotificationSessionState', () => {
  let events$: Subject<DocumentNotificationEvent>;
  let connectionState$: Subject<NotificationConnectionState>;
  let connect: ReturnType<typeof vi.fn>;
  let disconnect: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    events$ = new Subject<DocumentNotificationEvent>();
    connectionState$ = new Subject<NotificationConnectionState>();
    connect = vi.fn();
    disconnect = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        NotificationSessionState,
        {
          provide: DocumentNotificationService,
          useValue: { events$, connectionState$, connect, disconnect },
        },
      ],
    });
  });

  it('emits one visual-session event per eventId and ignores unknown events', () => {
    const state = TestBed.inject(NotificationSessionState);
    const received: string[] = [];
    state.events$.subscribe((event) => received.push(event.eventId));

    events$.next(notificationEventFixtures[0]);
    events$.next(notificationEventFixtures[0]);
    state.accept({ eventId: 'unknown', type: 'UNKNOWN' });
    events$.next(notificationEventFixtures[1]);

    expect(received).toEqual([
      notificationEventFixtures[0].eventId,
      notificationEventFixtures[1].eventId,
    ]);
  });

  it('starts and stops the connection for the current tab session', () => {
    const state = TestBed.inject(NotificationSessionState);

    state.start();
    state.stop();

    expect(connect).toHaveBeenCalledTimes(1);
    expect(disconnect).toHaveBeenCalledTimes(1);
  });

  it('allows separate tab state instances to receive the same event independently', () => {
    const firstTab = TestBed.inject(NotificationSessionState);
    const secondTab = TestBed.runInInjectionContext(() => new NotificationSessionState());
    const first: string[] = [];
    const second: string[] = [];
    firstTab.events$.subscribe((event) => first.push(event.eventId));
    secondTab.events$.subscribe((event) => second.push(event.eventId));

    events$.next(notificationEventFixtures[2]);

    expect(first).toEqual([notificationEventFixtures[2].eventId]);
    expect(second).toEqual([notificationEventFixtures[2].eventId]);
  });
});
